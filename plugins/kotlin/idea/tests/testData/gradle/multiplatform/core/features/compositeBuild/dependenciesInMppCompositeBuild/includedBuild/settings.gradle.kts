pluginManagement {
    plugins {
        kotlin("multiplatform") version "{{kgp_version}}"
        id("com.android.application") version "{{agp_version}}"
    }

    repositories {
        { { kts_kotlin_plugin_repositories } }
    }
}

include(":producer")
include(":consumer")
