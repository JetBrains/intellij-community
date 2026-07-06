pluginManagement {
    repositories {
        {{kts_kotlin_plugin_repositories}}
    }
    plugins {
        kotlin("multiplatform") version "{{kgp_version}}"
        {{android_application_compatible_plugin_id}} version "{{agp_version}}"
    }
}
