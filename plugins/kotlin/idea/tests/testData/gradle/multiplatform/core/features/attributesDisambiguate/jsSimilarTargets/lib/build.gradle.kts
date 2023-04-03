plugins {
    kotlin("multiplatform")
}

val attr = Attribute.of("disambiguity.attr", String::class.java)

kotlin {
    js("jsV1", IR) {
        nodejs()
        attributes.attribute(attr, "v1")
    }

    js("jsV2", IR) {
        browser()
        attributes.attribute(attr, "v2")
    }

    targetHierarchy.default {
        common {
            group("sharedJs") {
                withJs()
            }
        }
    }
}
