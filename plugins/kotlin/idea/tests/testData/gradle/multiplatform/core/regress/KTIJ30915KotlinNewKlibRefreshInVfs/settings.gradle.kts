pluginManagement {
    repositories {
        {{kts_kotlin_plugin_repositories}}
    }
    plugins {
        kotlin("multiplatform") version "{{kgp_version}}"
    }
}

rootProject.name = "KTIJ30915KotlinNewKlibRefreshInVfs"