plugins {
    kotlin("multiplatform")
    {{android_library_plugin_id}}
}

{{default_android_block}}

kotlin {
    js() // arbitrary
    jvm()
    {{androidTargetPlaceholder}}
}
