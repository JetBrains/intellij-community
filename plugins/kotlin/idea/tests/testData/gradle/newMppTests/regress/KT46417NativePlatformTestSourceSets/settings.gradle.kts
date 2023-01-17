pluginManagement {
    repositories {
        {{kts_kotlin_plugin_repositories}}
    }
    plugins {
        kotlin("multiplatform") version "{{kgp_version}}"
    }
}

include(":p1")
include(":p2")
