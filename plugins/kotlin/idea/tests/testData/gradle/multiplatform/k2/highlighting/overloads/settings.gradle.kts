pluginManagement {
    repositories {
        {{kts_kotlin_plugin_repositories}}
    }

    plugins {
        kotlin("multiplatform") version "{{kgp_version}}"
    }
}

include(":producer", ":consumer_source", ":consumer_binary")