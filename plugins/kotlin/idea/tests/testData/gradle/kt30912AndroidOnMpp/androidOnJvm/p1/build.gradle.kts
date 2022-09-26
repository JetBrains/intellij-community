plugins {
    id("com.android.library")
}

android {
    compileSdk = {{compile_sdk_version}}
}

dependencies {
    implementation(project(":p2"))
}
