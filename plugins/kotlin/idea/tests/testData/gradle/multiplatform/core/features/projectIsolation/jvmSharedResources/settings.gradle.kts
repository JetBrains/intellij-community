pluginManagement {
    repositories {
        {{kts_kotlin_plugin_repositories}}
    }
}

dependencyResolutionManagement {
    repositories {
        {{kts_kotlin_plugin_repositories}}
    }
}


rootProject.name = "jvmSharedResources"

include(":common", ":service", ":app")
