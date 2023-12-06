allprojects {
    repositories {
        { { kts_kotlin_plugin_repositories } }
        maven("$rootDir/repo")
    }
}
