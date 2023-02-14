plugins {
    id("com.android.library")
    kotlin("multiplatform")
}

android {
    compileSdkVersion({{compile_sdk_version}})
}

kotlin {
    android()
    jvm()
    sourceSets {
        getByName("commonMain") {
            dependencies {
                implementation(project(":p3"))
            }
        }
    }
}
