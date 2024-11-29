plugins {
    id("com.android.library")
    kotlin("multiplatform")
}

{{default_android_block}}

kotlin {
    {{androidTargetPlaceholder}}
    ios()

    sourceSets {
        val androidMain by getting {
            dependencies {
                api("org.jetbrains.kotlin.mpp.tests:androidLib:1.0")
            }
        }
    }
}
