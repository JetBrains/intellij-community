import org.jetbrains.kotlin.gradle.plugin.KotlinHierarchyTemplate

repositories {
    { { kts_kotlin_plugin_repositories } }
}

plugins {
    kotlin("multiplatform").version("{{kgp_version}}")
}

val attr = Attribute.of("disambiguity.attr", String::class.java)

kotlin {
    iosArm64("iosArm64V1") {
        attributes.attribute(attr, "v1")
    }

    iosArm64("iosArm64V2") {
        attributes.attribute(attr, "v2")
    }

    js("jsV1", IR) {
        nodejs()
        attributes.attribute(attr, "v1")
    }

    js("jsV2", IR) {
        browser()
        attributes.attribute(attr, "v2")
    }

    jvm("jvmV1") {
        attributes.attribute(attr, "v1")
    }

    jvm("jvmV2") {
        attributes.attribute(attr, "v2")
    }

    applyHierarchyTemplate(KotlinHierarchyTemplate.default) {
        common {
            group("sharedJs") {
                withJs()
            }
            group("sharedJvm") {
                withJvm()
            }
        }
    }
}