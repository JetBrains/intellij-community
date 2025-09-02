package com.example.kmpDependsOnAndroidKMPLibrary.android

import android.app.Activity
import android.os.Bundle

<!LINE_MARKER("descr='Run MainActivity'")!>class<!> MainActivity : Activity() {
    override fun <!LINE_MARKER("descr='Overrides function in Activity (android.app) Press ... to navigate'")!>onCreate<!>(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}