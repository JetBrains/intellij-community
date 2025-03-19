plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

{{default_android_block}}

kotlin {
    ios()
    {{androidTargetPlaceholder}}

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":direct"))
            }
        }
    }
}
