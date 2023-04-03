plugins {
    kotlin("multiplatform")
}

val attr = Attribute.of("disambiguity.attr", String::class.java)

kotlin {
    jvm("jvmV1") {
        withJava()
        attributes.attribute(attr, "v1")
    }

    jvm("jvmV2") {
        attributes.attribute(attr, "v2")
    }
}

dependencies {
    commonMainApi(project(":lib"))
}
