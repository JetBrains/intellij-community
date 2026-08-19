plugins {
    kotlin("multiplatform")
    {{android_library_plugin_id}}
}

{{default_android_block}}

kotlin {
    {{iosTargetPlaceholder}}
    {{androidTargetPlaceholder}}

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":direct"))
            }
        }
    }
}
