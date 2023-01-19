package com.example.app

import android.os.Bundle
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import android.view.Menu
import android.view.MenuItem
import com.example.mylibrary.AndroidMainApi
import com.example.mylibrary.CommonMainApi

class <lineMarker descr="Related XML file">MainActivity</lineMarker> : AppCompatActivity(), CommonMainApi, AndroidMainApi {

    override fun <lineMarker descr="Overrides function in AppCompatActivity (androidx.appcompat.app) Press Ctrl+U to navigate">onCreate</lineMarker>(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))

        findViewById<FloatingActionButton>(R.id.fab).setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
        }
    }

    override fun <lineMarker descr="Overrides function in Activity (android.app) Press Ctrl+U to navigate">onCreateOptionsMenu</lineMarker>(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun <lineMarker descr="Overrides function in Activity (android.app) Press Ctrl+U to navigate">onOptionsItemSelected</lineMarker>(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun <lineMarker descr="Implements function in AndroidMainApi (com.example.mylibrary) Press Ctrl+U to navigate">fragment</lineMarker>(fragment: Fragment) {
        println(fragment)
    }
}
