plugins {
    kotlin("multiplatform")
    {{android_library_plugin_id}}
}

{{default_android_block}}

val attr = Attribute.of("disambiguity.attr", String::class.java)

kotlin {
    {{androidTargetPlaceholder}}
    targets.getByName("android").attributes.attribute(attr, "jvm")
}

dependencies {
    commonMainApi(project(":lib"))
}
