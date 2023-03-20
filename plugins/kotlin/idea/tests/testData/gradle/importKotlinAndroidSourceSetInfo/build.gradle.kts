plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

android {
    compileSdk = {{compile_sdk_version}}
}

kotlin {
    android()
    jvm()
    linuxX64()
}
