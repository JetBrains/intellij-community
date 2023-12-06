plugins {
    kotlin("android")
    id("com.android.application")
}

{ { default_android_block } }

dependencies {
    implementation("org.jetbrains.kotlin.mpp.tests:direct:1.0")
}
