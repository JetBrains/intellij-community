plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

android {
    compileSdkVersion({{compile_sdk_version}})
    buildToolsVersion("{{build_tools_version}}")
}

kotlin {
    android()
    jvm()
    sourceSets {
        getByName("commonMain") {
            dependencies {
                api(project(":p3"))
            }
        }
    }
}

