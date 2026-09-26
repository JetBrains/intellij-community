plugins {
    kotlin("multiplatform")
    {{android_library_plugin_id}}
}

{{default_android_block}}

val attr = Attribute.of("disambiguity.attr", String::class.java)

kotlin {
    jvm() {
        attributes.attribute(attr, "jvm")
    }

    {{androidTargetPlaceholder}}

    targets.getByName("android").attributes.attribute(attr, "android") // the attribute conflicts with consumer -> androidJvm priority doesn't work
}
