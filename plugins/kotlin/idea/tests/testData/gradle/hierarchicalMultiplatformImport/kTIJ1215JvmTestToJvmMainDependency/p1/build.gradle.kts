plugins {
    id("com.android.library")
    kotlin("multiplatform")
}

android {
    compileSdkVersion({{compile_sdk_version}})
}

kotlin {
    android()
}
