pluginManagement {
	repositories {
        {{kts_kotlin_plugin_repositories}}
	}
	plugins {
		kotlin("multiplatform").version("{{kotlin_plugin_version}}")
		kotlin("android").version("{{kotlin_plugin_version}}")
	}

    resolutionStrategy {
        eachPlugin {
            if (requested.id.id.startsWith("com.android")) {
                useModule("com.android.tools.build:gradle:{{android_gradle_plugin_version}}")
            }
        }
    }
}

rootProject.name = "root"

include(":hmpp-lib", ":android-lib")