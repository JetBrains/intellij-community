plugins {
    kotlin("multiplatform")
    {{android_library_plugin_id}}
}

{{default_android_block}}

kotlin {
    {{androidTargetPlaceholder}}
    jvm()
    linuxX64()
}
