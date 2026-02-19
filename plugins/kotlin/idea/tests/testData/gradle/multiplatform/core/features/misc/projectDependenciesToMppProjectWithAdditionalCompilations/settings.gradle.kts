pluginManagement {
    repositories {
        {{kts_kotlin_plugin_repositories}}
    }

    plugins {
        kotlin("multiplatform") version "{{kgp_version}}"
        kotlin("jvm") version "{{kgp_version}}"
    }
}

rootProject.name = "root"

include(":libMpp", ":clientMpp", ":clientJvm", ":dependencyOfLib")
