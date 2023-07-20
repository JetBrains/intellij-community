plugins {
    id("com.android.application")
    kotlin("android")
}

{{default_android_block}}

dependencies {
    implementation(project(":kmmLib"))
}
