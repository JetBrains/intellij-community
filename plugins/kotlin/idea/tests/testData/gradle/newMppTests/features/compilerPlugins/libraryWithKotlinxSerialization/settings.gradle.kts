pluginManagement {
    repositories {
        {{kts_kotlin_plugin_repositories}}
    }
    plugins {
        kotlin("multiplatform") version "{{kgp_version}}"
        id("org.jetbrains.kotlin.plugin.serialization") version "{{kgp_version}}"
    }
}
