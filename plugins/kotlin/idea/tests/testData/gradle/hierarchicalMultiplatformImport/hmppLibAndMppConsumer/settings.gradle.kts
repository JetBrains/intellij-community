pluginManagement {
	repositories {
        {{kts_kotlin_plugin_repositories}}
	}
	plugins {
		kotlin("multiplatform").version("{{kotlin_plugin_version}}")
	}
}

rootProject.name = "my-app"
