pluginManagement {
    repositories {
        {{kts_kotlin_plugin_repositories}}
    }

    plugins {
        kotlin("multiplatform") version "{{kgp_version}}"
        kotlin("jvm") version "{{kgp_version}}"
        `java-library`
    }
}

include(":m1-kt-mpp")
include(":m2-java")
include(":m3-kt")
