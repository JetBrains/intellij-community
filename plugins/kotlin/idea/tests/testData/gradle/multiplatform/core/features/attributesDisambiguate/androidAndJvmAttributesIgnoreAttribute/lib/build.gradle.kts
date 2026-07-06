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

    {{androidTargetPlaceholder}} // there is no attribute the will conflict with consumer -> androidJvm priority works
}
