pluginManagement {
    repositories {
        { { kts_kotlin_plugin_repositories } }
    }
    plugins {
        kotlin("multiplatform") version "{{kgp_version}}"
        kotlin("android") version "{{kgp_version}}"
        id("com.android.library") version "{{agp_version}}"
        id("com.android.application") version "{{agp_version}}"
    }
}

include(":p1")
include(":p2")
include(":p3")
