plugins {
    kotlin("multiplatform") apply false
}

allprojects {
    repositories {
        {{kts_kotlin_plugin_repositories}}
    }
}