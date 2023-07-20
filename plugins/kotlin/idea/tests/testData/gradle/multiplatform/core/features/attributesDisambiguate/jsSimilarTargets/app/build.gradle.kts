plugins {
    kotlin("multiplatform")
}

val attr = Attribute.of("disambiguity.attr", String::class.java)

kotlin {
    js("jsV1", IR) {
        browser()
        attributes.attribute(attr, "v1")
    }

    js("jsV2", IR) {
        browser()
        attributes.attribute(attr, "v2")
    }
}

dependencies {
    commonMainApi(project(":lib"))
}
