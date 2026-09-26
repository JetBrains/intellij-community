plugins {
    {{android_library_kotlin_plugin_declaration}}
    {{android_library_plugin_id}}
}

{{default_android_block}}

{{android_main_source_set_open}}
dependencies {
    implementation(project(":jvmAndAndroidDirect"))
}
{{android_main_source_set_close}}
