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

rootProject.name = "jvmIncludedBuild"

include(":my-app")

includeBuild("my-plugin")
