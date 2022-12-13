@file:Suppress("OPT_IN_USAGE")

plugins {
    kotlin("multiplatform")
    id("com.android.application")
}

android {
    compileSdk = {{compile_sdk_version}}
    sourceSets.getByName("main").manifest.srcFile("src/androidMain/AndroidManifest.xml")
}

kotlin {
    jvm()
    android()

    sourceSets.commonMain.get().dependencies {
        implementation("org.jetbrains.sample:producerA:1.0.0-SNAPSHOT")
    }
}