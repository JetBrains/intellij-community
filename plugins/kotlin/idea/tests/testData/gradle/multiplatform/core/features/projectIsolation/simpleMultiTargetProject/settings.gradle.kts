pluginManagement {
    repositories {
        {{kts_kotlin_plugin_repositories}}
    }

    plugins {
        kotlin("multiplatform") version "{{kgp_version}}"
    }
}

dependencyResolutionManagement {
    repositories {
        {{kts_kotlin_plugin_repositories}}
    }
}

rootProject.name = "simpleMultiTargetProject"
include("app")
include("lib")
