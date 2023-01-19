pluginManagement {
    repositories {
        { { kts_kotlin_plugin_repositories } }
    }
    plugins {
        kotlin("multiplatform") version "{{kotlin_plugin_version}}"
    }
}

rootProject.name = "root"

include(":p1")
include(":p2")
include(":p3")
include(":p4")
include(":p5")
include(":p6")
