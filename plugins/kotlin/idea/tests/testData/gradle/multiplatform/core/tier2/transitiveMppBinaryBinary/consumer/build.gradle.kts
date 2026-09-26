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
                    implementation("org.jetbrains.kotlin.mpp.tests:direct:1.0")
            }
        }
    }
}
