pluginManagement {
    repositories {
        repositories {
            { { kts_kotlin_plugin_repositories } }
        }
    }
    plugins {
        kotlin("multiplatform") version "{{kgp_version}}"
        kotlin("android") version "{{kgp_version}}"
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
            { { kts_kotlin_plugin_repositories } }
        }
    }
}

include(":consumerA")

includeBuild("../producerBuild") {
    dependencySubstitution {
        substitute(module("org.jetbrains.sample:producerA")).using(project(":producerA"))
    }
}
