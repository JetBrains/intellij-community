pluginManagement {
    repositories {
        {{kts_kotlin_plugin_repositories}}
    }

    plugins {
        id("org.jetbrains.kotlin.multiplatform") version "{{kgp_version}}"
    }
}

dependencyResolutionManagement {
    repositories {
        {{kts_kotlin_plugin_repositories}}
    }
}


rootProject.name = "kmpWithCinteropLib"
include("app")
include("shared")