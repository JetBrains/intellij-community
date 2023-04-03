allprojects {
    repositories {
        { { kts_kotlin_plugin_repositories } }
    }
}

plugins {
    kotlin("multiplatform") apply false
    kotlin("jvm") apply false
}