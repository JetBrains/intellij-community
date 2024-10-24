plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

{{default_android_block}}

val attr = Attribute.of("disambiguity.attr", String::class.java)

kotlin {
    jvm() {
        attributes.attribute(attr, "jvm")
    }

    {{androidTargetPlaceholder}} {
        attributes.attribute(attr, "android") // the attribute conflicts with consumer -> androidJvm priority doesn't work
    }
}
