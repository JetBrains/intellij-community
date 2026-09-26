plugins {
    {{android_library_plugin_id}}
    kotlin("multiplatform")
}

{{default_android_block}}

kotlin {
    jvm()
    {{androidTargetPlaceholder}}
    sourceSets {
        named("commonMain") {
            dependencies {
                implementation(project(":p2"))
            }
        }
    }
}
