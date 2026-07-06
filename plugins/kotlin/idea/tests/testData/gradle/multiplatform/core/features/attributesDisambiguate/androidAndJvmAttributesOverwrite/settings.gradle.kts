pluginManagement {
    repositories {
        {{kts_kotlin_plugin_repositories}}
    }
    plugins {
        kotlin("multiplatform") version "{{kgp_version}}"
        {{android_root_plugins_with_versions}}
    }
}

include(":lib")
include(":app")