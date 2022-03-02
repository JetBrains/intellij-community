pluginManagement {
    repositories {
        { { kts_kotlin_plugin_repositories } }
    }

    plugins {
        kotlin("multiplatform") version "{{kotlin_plugin_version}}"
        kotlin("jvm") version "{{kotlin_plugin_version}}"
    }
}

include(":p1")
include(":p2")
include(":p3")
