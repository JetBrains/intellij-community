rootProject.name = "kmpSharedResourcesAndroidIOS"

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

include(":androidApp", ":shared")
