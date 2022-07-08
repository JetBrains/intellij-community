pluginManagement {
    repositories {
        repositories {
            { { kts_kotlin_plugin_repositories } }
        }
    }
    plugins {
        kotlin("multiplatform") version "{{kotlin_plugin_version}}"
        kotlin("jvm") version "{{kotlin_plugin_version}}"
        kotlin("android") version "{{kotlin_plugin_version}}"
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id.startsWith("com.android")) {
                useModule("com.android.tools.build:gradle:{{android_gradle_plugin_version}}")
            }
        }
    }

    dependencyResolutionManagement {
        repositories {
            { { kts_kotlin_plugin_repositories } }
        }
    }
}

include(":androidApp")
include(":jvmLibrary")
include(":multiplatformAndroidApp")
include(":multiplatformAndroidJvmIosLibrary")
include(":multiplatformAndroidJvmIosLibrary2")
include(":multiplatformAndroidLibrary")
include(":multiplatformJvmLibrary")
