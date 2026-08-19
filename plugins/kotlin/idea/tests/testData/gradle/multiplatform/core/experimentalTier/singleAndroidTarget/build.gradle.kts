plugins {
    kotlin("multiplatform")
    {{android_library_plugin_id}}
}


repositories {
    {{kts_kotlin_plugin_repositories}}
}

{{default_android_block}}

kotlin {
    {{androidTargetPlaceholder}}
    sourceSets {
        val androidMain by getting {
            {{android_main_kotlin_source_dirs}}
        }
    }
}
