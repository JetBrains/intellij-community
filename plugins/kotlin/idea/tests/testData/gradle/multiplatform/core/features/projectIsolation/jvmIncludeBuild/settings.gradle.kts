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

rootProject.name = "jvmIncludeBuild"

include(":my-app")

includeBuild("my-plugin")
