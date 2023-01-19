pluginManagement {
    repositories {
        {{kts_kotlin_plugin_repositories}}
    }
    plugins {
        kotlin("multiplatform") version "{{kotlin_plugin_version}}"
        id("com.android.application") version "{{android_gradle_plugin_version}}"
    }
}
