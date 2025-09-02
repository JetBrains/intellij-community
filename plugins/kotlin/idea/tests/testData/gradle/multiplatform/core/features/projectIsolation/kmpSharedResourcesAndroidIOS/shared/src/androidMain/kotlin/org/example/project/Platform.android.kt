package org.example.project

import android.os.Build

class AndroidPlatform : Platform {
    override val <!LINE_MARKER("descr='Implements property in Platform (org.example.project) Press ... to navigate'")!>name<!>: String = "Android ${Build.VERSION.SDK_INT}"
}

actual fun <!LINE_MARKER("descr='Has expects in kmpSharedResourcesAndroidIOS.shared.commonMain module'")!>getPlatform<!>(): Platform = AndroidPlatform()
