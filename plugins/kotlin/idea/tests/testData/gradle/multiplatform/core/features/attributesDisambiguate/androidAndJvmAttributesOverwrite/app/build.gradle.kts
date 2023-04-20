plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

{{default_android_block}}

val attr = Attribute.of("disambiguity.attr", String::class.java)

kotlin {
    android() {
        attributes.attribute(attr, "jvm")
    }
}

dependencies {
    commonMainApi(project(":lib"))
}
