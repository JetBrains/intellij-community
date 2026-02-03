plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    compileSdk = {{compile_sdk_version}}
    namespace = "org.jetbrains.kotlin.smoke.androidApp"
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("androidx.appcompat:appcompat:1.4.2")

    implementation(project(":multiplatformAndroidJvmIosLibrary"))
    implementation(project(":multiplatformJvmLibrary"))
    implementation(project(":multiplatformAndroidLibrary"))
    implementation(project(":jvmLibrary"))
}
