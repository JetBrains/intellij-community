pluginManagement {
    repositories {
        {{kts_kotlin_plugin_repositories}}
    }

    plugins {
        kotlin("multiplatform") version "{{kgp_version}}"
    }

    resolutionStrategy {
        eachPlugin {
            if (requested.id.id.startsWith("com.android")) {
                useModule("com.android.tools.build:gradle:{{agp_version}}")
            }
        }
    }

    dependencyResolutionManagement {
        repositories {
            {{kts_kotlin_plugin_repositories}}
        }
    }
}

rootProject.name = "rootProject"