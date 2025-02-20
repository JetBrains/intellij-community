package org.example.project

import android.app.Activity
import android.os.Bundle
import android.util.Log

<!LINE_MARKER("descr='Run MainActivity'")!>class<!> MainActivity : Activity() {
    override fun <!LINE_MARKER("descr='Overrides function in Activity (android.app) Press ... to navigate'")!>onCreate<!>(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val platformName = getPlatform().name
        Log.d("MainActivity", "Running on platform: $platformName")
    }
}
