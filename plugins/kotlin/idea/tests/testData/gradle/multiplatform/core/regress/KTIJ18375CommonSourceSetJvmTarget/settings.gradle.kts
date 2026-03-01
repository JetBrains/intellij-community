pluginManagement {
    repositories {
        { { kts_kotlin_plugin_repositories } }
    }

    plugins {
        val kotlinVersion = "{{kgp_version}}"
        kotlin("multiplatform") version kotlinVersion
        kotlin("jvm") version kotlinVersion
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

include(":consumer")
include(":producer")