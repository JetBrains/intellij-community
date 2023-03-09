plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    compileSdkVersion({{compile_sdk_version}})
    buildToolsVersion("{{build_tools_version}}")
}

dependencies {
    implementation("org.jetbrains.kotlin.mpp.tests:kmmLib:1.0")
}
