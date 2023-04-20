pluginManagement {
    repositories {
        {{kts_kotlin_plugin_repositories}}
    }

    resolutionStrategy {
        eachPlugin {
            if (requested.id.id.startsWith("com.android")) {
                useModule("com.android.tools.build:gradle:{{agp_version}}")
            }
        }
    }
}

include(":p1")
include(":p2")
