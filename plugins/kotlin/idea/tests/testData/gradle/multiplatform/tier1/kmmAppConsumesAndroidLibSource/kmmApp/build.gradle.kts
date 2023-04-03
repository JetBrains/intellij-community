plugins {
    id("com.android.library")
    kotlin("multiplatform")
}

{{default_android_block}}

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
