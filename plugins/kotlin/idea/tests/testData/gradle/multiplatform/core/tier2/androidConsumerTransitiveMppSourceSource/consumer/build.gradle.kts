plugins {
    kotlin("android")
    id("com.android.application")
}

{ { default_android_block } }

dependencies {
    implementation(project(":direct"))
}
