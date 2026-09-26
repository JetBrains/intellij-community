pluginManagement {
    repositories {
        { { kts_kotlin_plugin_repositories } }
    }
    plugins {
        kotlin("multiplatform") version "{{kgp_version}}"
        kotlin("android") version "{{kgp_version}}"
        {{android_library_plugin_id}} version "{{agp_version}}"
    }
}

rootProject.name = "rootProject"
