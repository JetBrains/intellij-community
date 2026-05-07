pluginManagement {
    repositories {
        { { kts_kotlin_plugin_repositories } }
    }

    plugins {
        kotlin("jvm") version "{{kgp_version}}"
        kotlin("plugin.serialization") version "{{kgp_version}}"
        kotlin("plugin.noarg") version "{{kgp_version}}"
        kotlin("plugin.lombok") version "{{kgp_version}}"
        kotlin("plugin.assignment") version "{{kgp_version}}"
        kotlin("plugin.power-assert") version "{{kgp_version}}"
    }
}

dependencyResolutionManagement {
    repositories {
        { { kts_kotlin_plugin_repositories } }
    }
}

rootProject.name = "project"

include("submodule")