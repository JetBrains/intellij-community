
repositories {
    {{kts_kotlin_plugin_repositories}}
}

plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

android {
    compileSdkVersion(26)
    buildToolsVersion("28.0.3")
}

kotlin {
    android()
}

