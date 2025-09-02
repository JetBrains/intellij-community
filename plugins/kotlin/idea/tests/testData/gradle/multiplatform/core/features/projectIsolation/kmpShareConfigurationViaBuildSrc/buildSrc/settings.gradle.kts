dependencyResolutionManagement {
    repositories {
        {{kts_kotlin_plugin_repositories}}
    }

    pluginManagement {
        repositories {
            {{kts_kotlin_plugin_repositories}}
        }
    }
}

rootProject.name = "buildSrc"