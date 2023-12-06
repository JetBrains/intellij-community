import org.jetbrains.kotlin.gradle.plugin.KotlinHierarchyTemplate

plugins {
    kotlin("multiplatform")
}

val attr = Attribute.of("disambiguity.attr", String::class.java)

kotlin {
    jvm("jvmV1") {
        attributes.attribute(attr, "v1")
    }

    jvm("jvmV2") {
        attributes.attribute(attr, "v2")
    }

    applyHierarchyTemplate(KotlinHierarchyTemplate.default) {
        common {
            group("sharedJvm") {
                withJvm()
            }
        }
    }
}
