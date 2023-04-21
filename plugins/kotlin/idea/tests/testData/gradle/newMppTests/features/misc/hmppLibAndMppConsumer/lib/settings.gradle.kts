pluginManagement {
    repositories {
        { { kts_kotlin_plugin_repositories } }
    }
    plugins {
        kotlin("multiplatform").version("{{kgp_version}}")
        id("com.android.library") version "{{agp_version}}"
    }
}

rootProject.name = "lib"

include("lib")
