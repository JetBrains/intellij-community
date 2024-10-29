pluginManagement {
    repositories {
        {{kts_kotlin_plugin_repositories}}
    }

    plugins {
        val kotlinVersion = "{{kgp_version}}"
        kotlin("jvm") version kotlinVersion
    }
}
rootProject.name = "kotlin-isolated"
include("a")
include("b")
