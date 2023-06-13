pluginManagement {
	repositories {
        {{kts_kotlin_plugin_repositories}}
	}
	plugins {
		kotlin("multiplatform") version "{{kgp_version}}"
		kotlin("android") version "{{kgp_version}}"
		id("com.android.library") version "{{agp_version}}"
	}
}

include(":kmmApp", ":androidLib")
