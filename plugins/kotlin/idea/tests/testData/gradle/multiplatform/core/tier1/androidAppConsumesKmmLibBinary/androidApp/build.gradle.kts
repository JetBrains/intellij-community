plugins {
    id("com.android.application")
    kotlin("android")
}

{{default_android_block}}

dependencies {
    implementation("org.jetbrains.kotlin.mpp.tests:kmmLib:1.0")
}
