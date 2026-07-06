plugins {
    kotlin("multiplatform")
    {{android_application_compatible_plugin_id}}
}

repositories {
    {{ kts_kotlin_plugin_repositories }}
}

{{default_android_block}}

kotlin {
    {{androidTargetPlaceholder}}
    {{iosTargetPlaceholder}}

    sourceSets {
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        {{android_host_test_source_dirs}}
    }
}
