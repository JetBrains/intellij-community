plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

{{default_android_block}}

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
