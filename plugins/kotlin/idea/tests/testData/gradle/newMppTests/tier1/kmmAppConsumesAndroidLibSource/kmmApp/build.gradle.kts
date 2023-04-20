plugins {
    id("com.android.library")
    kotlin("multiplatform")
}

android {
    compileSdkVersion({{compile_sdk_version}})
    buildToolsVersion("{{build_tools_version}}")
    namespace = "org.jetbrains.kotlin.mpp.tests"
}

kotlin {
    android()
    ios()

    sourceSets {
        val androidMain by getting {
            dependencies {
                api(project(":androidLib"))
            }
        }
    }
}
