plugins {
    id("com.android.library")
}

android {
    compileSdkVersion(26)
    buildToolsVersion("28.0.3")
}

dependencies {
    implementation(project(":p2"))
}
