plugins {
    kotlin("multiplatform")
    {{android_library_plugin_id}}
}

{{default_android_block}}

kotlin {
    {{androidTargetPlaceholder}}
    jvm()
    sourceSets {
        getByName("commonMain") {
            dependencies {
                api(project(":jvmAndAndroidTransitive"))
            }
        }
    }
}
