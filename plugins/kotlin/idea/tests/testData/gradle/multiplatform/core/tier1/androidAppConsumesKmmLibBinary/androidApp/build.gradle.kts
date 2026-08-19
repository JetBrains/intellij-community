plugins {
    {{android_application_compatible_plugin_id}}
    {{android_library_kotlin_plugin}}
}

{{default_android_block}}

{{android_main_source_set_open}}
dependencies {
    implementation("org.jetbrains.kotlin.mpp.tests:kmmLib:1.0")
}
{{android_main_source_set_close}}
