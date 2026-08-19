plugins {
    kotlin("multiplatform")
    {{android_application_compatible_plugin_id}}
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
