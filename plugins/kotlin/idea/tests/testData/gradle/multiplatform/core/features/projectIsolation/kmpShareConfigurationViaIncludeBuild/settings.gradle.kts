pluginManagement {
    repositories {
        {{kts_kotlin_plugin_repositories}}
    }
    includeBuild("build-logic")
}

dependencyResolutionManagement {
    repositories {
        {{kts_kotlin_plugin_repositories}}
    }
}

rootProject.name = "kmpShareConfigurationViaIncludeBuild"
include(":moduleOne")
include(":moduleTwo")