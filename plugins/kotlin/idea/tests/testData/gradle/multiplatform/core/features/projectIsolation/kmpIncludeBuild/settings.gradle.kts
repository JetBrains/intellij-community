pluginManagement {
    repositories {
        {{kts_kotlin_plugin_repositories}}
    }
}


rootProject.name = "kmpIncludeBuild"
includeBuild("lib")