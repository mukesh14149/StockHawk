package com.sam_chordas.android.stockhawk.ui;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.github.mikephil.charting.animation.Easing;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.realm.implementation.RealmLineData;
import com.github.mikephil.charting.data.realm.implementation.RealmLineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.sam_chordas.android.stockhawk.R;
import com.sam_chordas.android.stockhawk.Volley_Networking.AppController;
import com.sam_chordas.android.stockhawk.rest.HistoricalData;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;

import io.realm.Realm;
import io.realm.RealmChangeListener;
import io.realm.RealmConfiguration;
import io.realm.RealmResults;

public class LineChartActivity extends AppCompatActivity {

    Context mContext;
    String currentDate;
    String pastDate;
    LineChart chart;
    int lastId;
    RealmLineData realmLineData;
    Realm realm;
    String symbol;
    RealmChangeListener realmChangeListener;
    RealmResults<HistoricalData> results;
    RealmLineDataSet<HistoricalData> historicalDataRealmLineDataSet;
    ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_line_graph);
        Toolbar myToolbar = (Toolbar) findViewById(R.id.my_toolbar);
        setSupportActionBar(myToolbar);

        chart = (LineChart) findViewById(R.id.chart);
        progressDialog=new ProgressDialog(this);
        mContext=this;
        RealmConfiguration realmConfig = new RealmConfiguration.Builder(mContext).build();
        realm = Realm.getInstance(realmConfig);
        realmChangeListener=new RealmChangeListener() {
            @Override
            public void onChange() {
                setData();
            }
        };

        symbol=getIntent().getStringExtra("Symbol");
        getSupportActionBar().setTitle(symbol);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        myToolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });

        try {
            fetchData(urlBuild(symbol));
        } catch (IOException e) {
            e.printStackTrace();
        }
        setData();
    }

    String urlBuild(String stockInput){
        getDates();
        StringBuilder urlStringBuilder = new StringBuilder();
        try {
            urlStringBuilder.append("https://query.yahooapis.com/v1/public/yql?q=");
            urlStringBuilder.append(URLEncoder.encode("select * from yahoo.finance.historicaldata where symbol =  "
                    , "UTF-8"));
            urlStringBuilder.append(URLEncoder.encode("\""+stockInput+"\" and startDate = \""+pastDate+"\" and endDate = \""+currentDate+"\"", "UTF-8"));
            urlStringBuilder.append("&format=json&diagnostics=true&env=store%3A%2F%2Fdatatables."
                    + "org%2Falltableswithkeys&callback=");

        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return urlStringBuilder.toString();
    }
    void fetchData(String url) throws IOException {
        progressDialog.setMessage(mContext.getResources().getString(R.string.loading_desc));
        progressDialog.setTitle(mContext.getResources().getString(R.string.loading));
        progressDialog.show();
        JsonObjectRequest jsonObjectRequest=new JsonObjectRequest(url,
                new com.android.volley.Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        Log.d("TEST",response.toString());
                        progressDialog.dismiss();
                        try {
                            JSONObject jsonObject = response.getJSONObject("query");
                            if(jsonObject.getInt("count")>1){
                                jsonObject=jsonObject.getJSONObject("results");
                                if(jsonObject!=null) {
                                    JSONArray resultsArray = jsonObject.getJSONArray("quote");
                                    saveData(resultsArray);
                                }
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                },
                new com.android.volley.Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.d("TEST",error.toString());
                        progressDialog.dismiss();
                        Toast.makeText(mContext,mContext.getResources().getString(R.string.network_toast),Toast.LENGTH_LONG).show();
                    }
                });
        AppController.getInstance().addToRequestQueue(jsonObjectRequest);
    }

    private void saveData(final JSONArray jsonArray){

        realm.executeTransactionAsync(new Realm.Transaction() {
            @Override
            public void execute(Realm realm) {
                for (int i = jsonArray.length() - 1; i >= 0; i--) {
                    JSONObject object = null;
                    try {
                        object = jsonArray.getJSONObject(i);
                        realm.copyToRealm(new HistoricalData(++lastId, symbol, object.getString("Date"), Float.parseFloat(object.getString("High"))));
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

            }
        });
    }
    private void setData() {

        results = realm.where(HistoricalData.class).equalTo("stock",symbol).findAll();
        results.addChangeListener(realmChangeListener);
        if(results.size()>0){
            historicalDataRealmLineDataSet=new RealmLineDataSet<HistoricalData>(results,"value","id");

            historicalDataRealmLineDataSet.setFillAlpha(110);
            historicalDataRealmLineDataSet.setFillColor(Color.parseColor("#ff303030"));

            historicalDataRealmLineDataSet.enableDashedLine(10f, 5f, 0f);
            historicalDataRealmLineDataSet.enableDashedHighlightLine(10f, 5f, 0f);
            historicalDataRealmLineDataSet.setColor(Color.BLACK);
            historicalDataRealmLineDataSet.setCircleColor(Color.BLACK);
            historicalDataRealmLineDataSet.setLineWidth(1f);
            historicalDataRealmLineDataSet.setCircleSize(2.5f);
            historicalDataRealmLineDataSet.setDrawCircleHole(false);
            historicalDataRealmLineDataSet.setValueTextSize(9f);
            historicalDataRealmLineDataSet.setDrawFilled(true);

            ArrayList<ILineDataSet> dataSets = new ArrayList<ILineDataSet>();
            dataSets.add(historicalDataRealmLineDataSet);
            realmLineData=new RealmLineData(results,"date",dataSets);
            chart.setAutoScaleMinMaxEnabled(true);
            chart.getLegend().setEnabled(false);
            chart.setData(realmLineData);
            chart.animateX(3000,Easing.EasingOption.Linear);}
    }

    public void getDates() {
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        Calendar cal = Calendar.getInstance();
        currentDate=dateFormat.format(cal.getTime());
        RealmResults<HistoricalData> historicalData = realm.where(HistoricalData.class).equalTo("stock",symbol).findAll();
        if(historicalData.size()>0) {
            pastDate = historicalData.last().getDate();
            lastId=historicalData.last().getId();
            Log.d("TEST",lastId+"");
        }
        else{
            lastId=-1;
            cal.add(Calendar.YEAR,-1);
            pastDate=dateFormat.format(cal.getTime());}
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.line_graph, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_refresh:
                try {
                    fetchData(urlBuild(symbol));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);

        }
    }
}
