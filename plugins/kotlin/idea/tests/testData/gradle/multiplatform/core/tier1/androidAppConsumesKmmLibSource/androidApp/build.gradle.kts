plugins {
    {{android_application_compatible_plugin_id}}
    {{android_library_kotlin_plugin}}
}

{{default_android_block}}

{{android_main_source_set_open}}
dependencies {
    implementation(project(":kmmLib"))
}
{{android_main_source_set_close}}
