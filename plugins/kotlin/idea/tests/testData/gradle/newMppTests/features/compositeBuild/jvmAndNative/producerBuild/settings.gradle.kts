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

include(":producerA")
