plugins {
    id("com.android.library")
    kotlin("multiplatform")
}
android {
    compileSdk = { { compile_sdk_version } }
}
kotlin {
    jvm()
    android()
    sourceSets {
        named("commonMain") {
            dependencies {
                implementation(project(":p2"))
            }
        }
    }
}
