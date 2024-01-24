plugins {
    kotlin("multiplatform") apply false
}

allprojects {
    group = "a"
    version = "1.0"

    repositories {
        maven("$rootDir/repo")
        {{kts_kotlin_plugin_repositories}}
    }
}