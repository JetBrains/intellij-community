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

include(":consumer")
include(":producer")