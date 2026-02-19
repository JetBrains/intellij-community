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

rootProject.name = "kmpTestDependenciesAcrossModules"

include("jvm-module")
include("mpp-module")
include("test-utils")
