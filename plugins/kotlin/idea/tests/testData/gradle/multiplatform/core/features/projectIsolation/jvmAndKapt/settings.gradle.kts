pluginManagement {
    repositories {
        {{kts_kotlin_plugin_repositories}}
    }
}


@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositories {
        {{kts_kotlin_plugin_repositories}}
    }
}


rootProject.name = "jvmAndKapt"

include("module1")
include("module2")
