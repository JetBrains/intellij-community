pluginManagement {
    repositories {
        {{kts_kotlin_plugin_repositories}}
    }
    plugins {
        kotlin("multiplatform").version("{{kotlin_plugin_version}}")
    }
}

rootProject.name = "SimpleProjectToProject"

include(":a")
include(":b")
