plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    compileSdkVersion({{compile_sdk_version}})
}

dependencies {
    implementation(project(":p2"))
}
