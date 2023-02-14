package com.example.app

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class <lineMarker descr="Run Test">ExampleInstrumentedTest</lineMarker> {
    @Test
    fun <lineMarker descr="Run Test">useAppContext</lineMarker>() {
        // Context of the app under test.
        println(InstrumentationRegistry.getInstrumentation().targetContext)
    }
}
