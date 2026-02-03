plugins {
    kotlin("android")
    id("com.android.library")
}

{{default_android_block}}

val attr = Attribute.of("disambiguity.attr", String::class.java)

configurations.all {
    attributes.attribute(attr, "jvm")
}

dependencies {
    api(project(":lib"))
}
