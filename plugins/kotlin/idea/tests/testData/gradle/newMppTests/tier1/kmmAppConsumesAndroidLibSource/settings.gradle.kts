pluginManagement {
	repositories {
        {{kts_kotlin_plugin_repositories}}
	}
	plugins {
		kotlin("multiplatform") version "{{kotlin_plugin_version}}"
		kotlin("android") version "{{kotlin_plugin_version}}"
		id("com.android.library") version "{{android_gradle_plugin_version}}"
	}
}

include(":kmmApp", ":androidLib")
