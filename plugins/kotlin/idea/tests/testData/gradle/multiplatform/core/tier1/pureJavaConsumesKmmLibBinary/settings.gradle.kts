pluginManagement {
    repositories {
        {{kts_kotlin_plugin_repositories}}
    }
    plugins {
        kotlin("multiplatform") version "{{kgp_version}}"
        kotlin("jvm") version "{{kgp_version}}"
    }
}

include(":javaOnly")
include(":kmmLib")
