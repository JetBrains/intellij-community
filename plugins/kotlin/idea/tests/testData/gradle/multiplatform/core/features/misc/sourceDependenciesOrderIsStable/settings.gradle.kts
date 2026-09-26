pluginManagement {
    repositories {
        {{kts_kotlin_plugin_repositories}}
    }

    plugins {
        kotlin("multiplatform") version "{{kgp_version}}"
    }
}

rootProject.name = "my-app"

include("lib1", "lib2", "consumer")
