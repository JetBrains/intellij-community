pluginManagement {
    repositories {
        {{kts_kotlin_plugin_repositories}}
    }

    plugins {
        kotlin("multiplatform") version "{{kgp_version}}"
    }
}

extensions.extraProperties["kotlin.native.distribution.baseDownloadUrl"] =
    "https://cache-redirector.jetbrains.com/download.jetbrains.com/kotlin/native/builds"