plugins {
    id("com.android.library")
    kotlin("multiplatform")
}

{{default_android_block}}

kotlin {
    {{androidTargetPlaceholder}}
    {{iosTargetPlaceholder}}

    sourceSets {
        val androidMain by getting {
            dependencies {
                api(project(":androidLib"))
            }
        }
    }
}
