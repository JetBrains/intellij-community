plugins {
    {{android_library_kotlin_plugin}}
    {{android_library_plugin_id}}
}

{{default_android_block}}

val attr = Attribute.of("disambiguity.attr", String::class.java)

configurations.all {
    attributes.attribute(attr, "jvm")
}

{{android_main_source_set_open}}
dependencies {
    api(project(":lib"))
}
{{android_main_source_set_close}}
