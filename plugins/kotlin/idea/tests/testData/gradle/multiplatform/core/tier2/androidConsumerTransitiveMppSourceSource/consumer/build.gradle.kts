plugins {
    {{android_library_kotlin_plugin}}
    {{android_application_compatible_plugin_id}}
}

{ { default_android_block } }

{{android_main_source_set_open}}
dependencies {
    implementation(project(":direct"))
}
{{android_main_source_set_close}}
