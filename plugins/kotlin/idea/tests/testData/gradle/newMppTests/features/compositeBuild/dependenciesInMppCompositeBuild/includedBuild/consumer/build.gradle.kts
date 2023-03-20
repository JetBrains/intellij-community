plugins {
    kotlin("multiplatform")
    id("com.android.application")
}

{{default_android_block}}

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
