plugins {
    kotlin("multiplatform")
    id("com.android.application")
}

{{default_android_block}}

kotlin {
    {{iosTargetPlaceholder}}
    {{androidTargetPlaceholder}}

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":producer"))
                api(project(":"))
            }
        }
    }
}
