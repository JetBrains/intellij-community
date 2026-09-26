package org.example.project

interface <!LINE_MARKER("descr='Is implemented by AndroidPlatform (org.example.project) IOSPlatform (org.example.project) Press ... to navigate'")!>Platform<!> {
    val <!LINE_MARKER("descr='Is implemented in AndroidPlatform (org.example.project) IOSPlatform (org.example.project) Press ... to navigate'")!>name<!>: String
}

expect fun <!LINE_MARKER("descr='Has actuals in [kmpSharedResourcesAndroidIOS.shared.iosMain, kmpSharedResourcesAndroidIOS.shared.main] modules'; targets=[(text=kmpSharedResourcesAndroidIOS.shared.iosMain); (text=kmpSharedResourcesAndroidIOS.shared.main)]")!>getPlatform<!>(): Platform
