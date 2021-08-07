pluginManagement {
    repositories {
        { { kts_kotlin_plugin_repositories } }
    }
    plugins {
        kotlin("multiplatform") version "{{kotlin_plugin_version}}"
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id.startsWith("com.android")) {
                useModule("com.android.tools.build:gradle:4.0.1")
            }
        }
    }
}

include(":p1")
