pluginManagement {
    repositories {
        { { kts_kotlin_plugin_repositories } }
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.namespace == "com.android" || requested.id.name == "kotlin-android-extensions") {
                useModule("com.android.tools.build:gradle:4.1.2")
            }
        }
    }
}
