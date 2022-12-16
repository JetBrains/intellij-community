plugins {
    kotlin("multiplatform")
    id("com.android.application")
}

android {
    compileSdkVersion({{compile_sdk_version}})
    buildToolsVersion("{{build_tools_version}}")
    namespace = "org.jetbrains.kotlin.mpp.tests"
}

kotlin {
    ios()
    android()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":producer"))
                api(project(":"))
            }
        }
    }
}
