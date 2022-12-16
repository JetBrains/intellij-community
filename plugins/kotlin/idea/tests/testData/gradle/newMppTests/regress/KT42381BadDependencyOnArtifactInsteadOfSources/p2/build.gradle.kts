plugins {
    kotlin("multiplatform")
    id("com.android.library")
}
android {
    compileSdkVersion({{compile_sdk_version}})
}
kotlin {
    js() // arbitrary
    jvm()
    android()
}
