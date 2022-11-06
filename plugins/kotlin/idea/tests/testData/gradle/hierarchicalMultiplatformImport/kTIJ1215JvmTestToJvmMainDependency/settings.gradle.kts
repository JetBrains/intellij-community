pluginManagement {
    repositories {
        {{kts_kotlin_plugin_repositories}}
    }
    plugins {
        kotlin("multiplatform") version "{{kotlin_plugin_version}}"
        kotlin("android") version "{{kotlin_plugin_version}}"
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id.startsWith("com.android")) {
                useModule("com.android.tools.build:gradle:4.1.3")
            }
        }
    }
}

include(":p1")
include(":p2")

