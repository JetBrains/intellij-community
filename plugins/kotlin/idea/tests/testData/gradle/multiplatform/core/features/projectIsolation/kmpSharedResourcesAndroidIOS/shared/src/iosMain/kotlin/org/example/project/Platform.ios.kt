package org.example.project

import platform.UIKit.UIDevice

class IOSPlatform: Platform {
    override val <!LINE_MARKER("descr='Implements property in Platform (org.example.project) Press ... to navigate'")!>name<!>: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
}

actual fun <!LINE_MARKER("descr='Has expects in kmpSharedResourcesAndroidIOS.shared.commonMain module'")!>getPlatform<!>(): Platform = IOSPlatform()
