import org.jetbrains.kotlin.gradle.plugin.KotlinHierarchyTemplate

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

    applyHierarchyTemplate(KotlinHierarchyTemplate.default) {
        common {
            group("sharedJs") {
                withJs()
            }
        }
    }
}
