package com.stephen.awesomenavigator;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.geometry.GeometryEngine;
import com.esri.arcgisruntime.geometry.Point;
import com.esri.arcgisruntime.geometry.Polyline;
import com.esri.arcgisruntime.geometry.SpatialReferences;
import com.esri.arcgisruntime.loadable.LoadStatus;
import com.esri.arcgisruntime.mapping.ArcGISMap;
import com.esri.arcgisruntime.mapping.Basemap;
import com.esri.arcgisruntime.mapping.view.Callout;
import com.esri.arcgisruntime.mapping.view.DefaultMapViewOnTouchListener;
import com.esri.arcgisruntime.mapping.view.Graphic;
import com.esri.arcgisruntime.mapping.view.GraphicsOverlay;
import com.esri.arcgisruntime.mapping.view.LocationDisplay;
import com.esri.arcgisruntime.mapping.view.MapRotationChangedEvent;
import com.esri.arcgisruntime.mapping.view.MapRotationChangedListener;
import com.esri.arcgisruntime.mapping.view.MapView;
import com.esri.arcgisruntime.security.AuthenticationManager;
import com.esri.arcgisruntime.security.DefaultAuthenticationChallengeHandler;
import com.esri.arcgisruntime.security.OAuthConfiguration;
import com.esri.arcgisruntime.symbology.SimpleLineSymbol;
import com.esri.arcgisruntime.symbology.SimpleMarkerSymbol;
import com.esri.arcgisruntime.tasks.networkanalysis.Route;
import com.esri.arcgisruntime.tasks.networkanalysis.RouteParameters;
import com.esri.arcgisruntime.tasks.networkanalysis.RouteResult;
import com.esri.arcgisruntime.tasks.networkanalysis.RouteTask;
import com.esri.arcgisruntime.tasks.networkanalysis.Stop;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {
    private MapView mMapView;
    private ArcGISMap myMap;
    private LocationDisplay mLocationDisplay;

    private MenuItem locatonMode;
    private MenuItem walkingMode;
    private MenuItem drivingMode;
    private MenuItem mapCenterMode;
    private MenuItem turnOffGPS;
    private ImageView northArrow;
    private Matrix matrix;
    private LocationDisplay myLocation;
    private Point wgs84Pt;

    private GraphicsOverlay mGraphicsOverlay;
    private Point mStart;
    private Point mEnd;

    private void setupMap() {
        if (mMapView != null) {
            Basemap.Type basemapType = Basemap.Type.STREETS_VECTOR;
            double latitude = -43.52;
            double longitude = 172.57;
            int levelOfDetail = 11;
            myMap = new ArcGISMap(basemapType, latitude, longitude, levelOfDetail);
            mMapView.setMap(myMap);
        }
    }

    private void createGraphicsOverlay() {
        mGraphicsOverlay = new GraphicsOverlay();
        mMapView.getGraphicsOverlays().add(mGraphicsOverlay);
    }

    private void setMapMarker(Point location, SimpleMarkerSymbol.Style style, int markerColor, int outlineColor) {
        float markerSize = 8.0f;
        float markerOutlineThickness = 2.0f;
        SimpleMarkerSymbol pointSymbol = new SimpleMarkerSymbol(style, markerColor, markerSize);
        pointSymbol.setOutline(new SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, outlineColor, markerOutlineThickness));
        Graphic pointGraphic = new Graphic(location, pointSymbol);
        mGraphicsOverlay.getGraphics().add(pointGraphic);
    }

    private void setStartMarker(Point location) {
        mGraphicsOverlay.getGraphics().clear();
        setMapMarker(location, SimpleMarkerSymbol.Style.DIAMOND, Color.rgb(226, 119, 40), Color.BLUE);
        mStart = location;
        mEnd = null;
    }

    private void setEndMarker(Point location) {
        setMapMarker(location, SimpleMarkerSymbol.Style.SQUARE, Color.rgb(40, 119, 226), Color.RED);
        mEnd = location;
        findRoute();
    }

    private void mapClicked(Point location) {
        if (mStart == null) {
            // Start is not set, set it to a tapped location
            setStartMarker(location);
        } else if (mEnd == null) {
            // End is not set, set it to the tapped location then find the route
            setEndMarker(location);
        } else {
            // Both locations are set; re-set the start to the tapped location
            setStartMarker(location);
        }
    }

    private void setupLocationDisplay() {
        mLocationDisplay = mMapView.getLocationDisplay();
        mLocationDisplay.addDataSourceStatusChangedListener(dataSourceStatusChangedEvent -> {

            // If LocationDisplay started OK or no error is reported, then continue.
            if (dataSourceStatusChangedEvent.isStarted() || dataSourceStatusChangedEvent.getError() == null) {
                return;
            }

            int requestPermissionsCode = 2;
            String[] requestPermissions = new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION};

            // If an error is found, handle the failure to start.
            // Check permissions to see if failure may be due to lack of permissions.
            if (!(ContextCompat.checkSelfPermission(MainActivity.this, requestPermissions[0]) == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(MainActivity.this, requestPermissions[1]) == PackageManager.PERMISSION_GRANTED)) {

                // If permissions are not already granted, request permission from the user.
                ActivityCompat.requestPermissions(MainActivity.this, requestPermissions, requestPermissionsCode);
            } else {

                // Report other unknown failure types to the user - for example, location services may not
                // be enabled on the device.
                String message = String.format("Error in DataSourceStatusChangedListener: %s", dataSourceStatusChangedEvent
                        .getSource().getLocationDataSource().getError().getMessage());
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
        mLocationDisplay.setAutoPanMode(LocationDisplay.AutoPanMode.COMPASS_NAVIGATION);
        mLocationDisplay.startAsync();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        // If request is cancelled, the result arrays are empty.
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

            // Location permission was granted. This would have been triggered in response to failing to start the
            // LocationDisplay, so try starting this again.
            mLocationDisplay.startAsync();
        } else {

            // If permission was denied, show toast to inform user what was chosen. If LocationDisplay is started again,
            // request permission UX will be shown again, option should be shown to allow never showing the UX again.
            // Alternative would be to disable functionality so request is not shown again.
            Toast.makeText(MainActivity.this, getResources().getString(R.string.location_permission_denied), Toast.LENGTH_SHORT).show();
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        northArrow = (ImageView)findViewById(R.id.imageView1);

        // *** ADD ***
        mMapView = findViewById(R.id.map);
        setupMap();
        createGraphicsOverlay();
        setupOAuthManager();
        setupLocationDisplay();

        mMapView.setOnTouchListener(new DefaultMapViewOnTouchListener(this, mMapView) {
            @Override public boolean onSingleTapConfirmed(MotionEvent e) {
                android.graphics.Point screenPoint = new android.graphics.Point(
                        Math.round(e.getX()),
                        Math.round(e.getY()));
                Point mapPoint = mMapView.screenToLocation(screenPoint);
                mapClicked(mapPoint);
                return super.onSingleTapConfirmed(e);
            }
        });

        mMapView.addMapRotationChangedListener(new MapRotationChangedListener() {
            @Override
            public void mapRotationChanged(MapRotationChangedEvent mapRotationChangedEvent) {
                double rotationAngle = mapRotationChangedEvent.getSource().getMapRotation();
                final Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.northarrow60);

                matrix = new Matrix();
                matrix.setScale(1, 1);
                matrix.postRotate((float) -rotationAngle, bitmap.getWidth()/2,bitmap.getHeight()/2);
                Bitmap updatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(),
                        bitmap.getHeight(), matrix, true);
                BitmapDrawable updatedNorthArrow = new
                        BitmapDrawable(getApplicationContext().getResources(),updatedBitmap);

                northArrow.setImageDrawable(updatedNorthArrow);
            }
        });

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(myFab1_OnClickListener);
    }

    private View.OnClickListener myFab1_OnClickListener = new View.OnClickListener()
    {
        @Override
        public void onClick(View v) {
            myLocation = mMapView.getLocationDisplay();
            Point mapPt = myLocation.getMapLocation();
            wgs84Pt = (Point) GeometryEngine.project(mapPt, SpatialReferences.getWgs84());

            if (myLocation.isStarted() ) {
                createCallout();
            }else {
                Toast.makeText(MainActivity.this, "Fab1 is clicked", Toast.LENGTH_LONG).show();
            }
        }
    };

    private void createCallout(){
        Callout callout = mMapView.getCallout();
        LayoutInflater inflater = getLayoutInflater();
        View calloutContent = inflater.inflate(R.layout.callout_content,(ViewGroup)null);
        TextView tv_lat = (TextView)calloutContent.findViewById(R.id.latitude);
        TextView tv_lon = (TextView)calloutContent.findViewById(R.id.longitude);
        TextView tv_heading = (TextView)calloutContent.findViewById(R.id.heading);
        TextView tv_speed = (TextView)calloutContent.findViewById(R.id.speed);
        TextView tv_accuracy = (TextView)calloutContent.findViewById(R.id.accuracy);

        Callout.Style calloutStyle = new Callout.Style(getApplicationContext(), R.xml.calloutstyle2);
        callout.setStyle(calloutStyle);

//        calloutStyle.setLeaderPosition(Callout.Style.LeaderPosition.LOWER_LEFT_CORNER);
//        calloutStyle.setLeaderLength(60);

        Callout.ShowOptions calloutOption = new Callout.ShowOptions();

        if (callout.isShowing()) {
            callout.dismiss();
        }else {
            callout.setStyle(calloutStyle);
            String latString = "Lat : " + String.format(Locale.US, "%.6f", wgs84Pt.getY());
            String lonString = "Lon : " + String.format(Locale.US, "%.6f", wgs84Pt.getX());
            String headingString = "Heading : " + String.format(Locale.US, "%.6f", myLocation.getHeading());
            String speedString = "Speed : " + String.format(Locale.US, "%.6f",myLocation.getLocation().getVelocity());
            String accuracyString = "Acuracy: " + String.format(Locale.US, "%.6f", myLocation.getLocation().getHorizontalAccuracy());

            tv_lat.setText(latString);
            tv_lon.setText(lonString);
            tv_heading.setText(headingString);
            tv_speed.setText(speedString);
            tv_accuracy.setText(accuracyString);

            calloutOption.setAnimateCallout(true);
            calloutOption.setRecenterMap(true);
            callout.setShowOptions(calloutOption);
            callout.setLocation(wgs84Pt);
            callout.setContent(calloutContent);
            callout.show();
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        getMenuInflater().inflate(R.menu.menu_nav, menu);

        locatonMode = menu.findItem(R.id.location);
        walkingMode = menu.findItem(R.id.walking);
        drivingMode = menu.findItem(R.id.driving);
        mapCenterMode = menu.findItem(R.id.mapcenter);
        turnOffGPS = menu.findItem(R.id.turn_off_gps);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        LocationDisplay myLocation = mMapView.getLocationDisplay();
        switch (item.getItemId()){
            case R.id.action_item1:
                myMap.setBasemap(Basemap.createStreets());
                return true;
            case R.id.action_item2:
                myMap.setBasemap(Basemap.createImagery());
                return true;
            case R.id.action_item3:
                myMap.setBasemap(Basemap.createTopographic());
                return true;
            case R.id.action_item4:
                myMap.setBasemap(Basemap.createOpenStreetMap());
                return true;
            case R.id.location:
                locatonMode.setChecked(true);
                myLocation.setAutoPanMode(LocationDisplay.AutoPanMode.OFF);
                myLocation.startAsync();
                return true;
            case R.id.walking:
                walkingMode.setChecked(true);
                myLocation.setAutoPanMode(LocationDisplay.AutoPanMode.COMPASS_NAVIGATION);
                myLocation.startAsync();
                myLocation.setInitialZoomScale(2000);
                return true;
            case R.id.driving:
                drivingMode.setChecked(true);
                myLocation.setAutoPanMode(LocationDisplay.AutoPanMode.NAVIGATION);
                myLocation.startAsync();
                myLocation.setNavigationPointHeightFactor((float) .3);
                myLocation.setInitialZoomScale(6000);
                return true;
            case R.id.mapcenter:
                mapCenterMode.setChecked(true);
                myLocation.setAutoPanMode(LocationDisplay.AutoPanMode.RECENTER);
                myLocation.startAsync();
                myLocation.setWanderExtentFactor((float) .3);
                return true;
            case R.id.turn_off_gps:
                turnOffGPS.setChecked(true);
                myLocation.stop();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onPause() {
        if (mMapView != null) {
            mMapView.pause();
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mMapView != null) {
            mMapView.resume();
        }
    }

    @Override
    protected void onDestroy() {
        if (mMapView != null) {
            mMapView.dispose();
        }
        super.onDestroy();
    }

    private void setupOAuthManager() {
        String clientId = getResources().getString(R.string.client_id);
        String redirectUrl = getResources().getString(R.string.redirect_url);

        try {
            OAuthConfiguration oAuthConfiguration = new OAuthConfiguration("https://www.arcgis.com", clientId, redirectUrl);
            DefaultAuthenticationChallengeHandler authenticationChallengeHandler = new DefaultAuthenticationChallengeHandler(this);
            AuthenticationManager.setAuthenticationChallengeHandler(authenticationChallengeHandler);
            AuthenticationManager.addOAuthConfiguration(oAuthConfiguration);
        } catch (MalformedURLException e) {
            showError(e.getMessage());
        }
    }
    private void showError(String message) {
        Log.d("FindRoute", message);
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
    }

    private void findRoute() {
        String routeServiceURI = getResources().getString(R.string.routing_url);
        final RouteTask solveRouteTask = new RouteTask(getApplicationContext(), routeServiceURI);
        solveRouteTask.loadAsync();
        solveRouteTask.addDoneLoadingListener(() -> {
            if (solveRouteTask.getLoadStatus() == LoadStatus.LOADED) {
                final ListenableFuture<RouteParameters> routeParamsFuture = solveRouteTask.createDefaultParametersAsync();
                routeParamsFuture.addDoneListener(() -> {
                    try {
                        RouteParameters routeParameters = routeParamsFuture.get();
                        List<Stop> stops = new ArrayList<>();
                        stops.add(new Stop(mStart));
                        stops.add(new Stop(mEnd));
                        routeParameters.setStops(stops);
                        final ListenableFuture<RouteResult> routeResultFuture = solveRouteTask.solveRouteAsync(routeParameters);
                        routeResultFuture.addDoneListener(() -> {
                            try {
                                RouteResult routeResult = routeResultFuture.get();
                                Route firstRoute = routeResult.getRoutes().get(0);
                                Polyline routePolyline = firstRoute.getRouteGeometry();
                                SimpleLineSymbol routeSymbol = new SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.BLUE, 4.0f);
                                Graphic routeGraphic = new Graphic(routePolyline, routeSymbol);
                                mGraphicsOverlay.getGraphics().add(routeGraphic);
                            } catch (InterruptedException | ExecutionException e) {
                                showError("Solve RouteTask failed " + e.getMessage());
                            }
                        });
                    } catch (InterruptedException | ExecutionException e) {
                        showError("Cannot create RouteTask parameters " + e.getMessage());
                    }
                });
            } else {
                showError("Unable to load RouteTask " + solveRouteTask.getLoadStatus().toString());
            }
        });
    }
}
