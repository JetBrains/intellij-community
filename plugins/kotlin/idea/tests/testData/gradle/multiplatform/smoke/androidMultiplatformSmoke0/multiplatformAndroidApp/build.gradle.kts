plugins {
    id("com.android.application")
    kotlin("multiplatform")
}

android {
    compileSdk = {{compile_sdk_version}}
    namespace = "org.jetbrains.kotlin.smoke.multiplatformAndroidApp"
    defaultConfig {
        minSdk = 30
    }
}

kotlin {
    android()

    val commonMain by sourceSets.getting

    commonMain.dependencies {
        implementation(kotlin("stdlib-jdk8"))
        implementation("androidx.appcompat:appcompat:1.4.2")
        implementation("com.squareup.okio:okio:3.2.0")

        implementation(project(":multiplatformAndroidJvmIosLibrary"))
        implementation(project(":multiplatformJvmLibrary"))
        implementation(project(":multiplatformAndroidLibrary"))
        implementation(project(":jvmLibrary"))
    }
}
