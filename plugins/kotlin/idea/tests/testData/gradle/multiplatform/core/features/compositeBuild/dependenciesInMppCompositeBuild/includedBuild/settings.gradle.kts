pluginManagement {
    plugins {
        kotlin("multiplatform") version "{{kgp_version}}"
        {{android_application_compatible_plugin_id}} version "{{agp_version}}"
    }

    repositories {
        { { kts_kotlin_plugin_repositories } }
    }
}

include(":producer")
include(":consumer")
