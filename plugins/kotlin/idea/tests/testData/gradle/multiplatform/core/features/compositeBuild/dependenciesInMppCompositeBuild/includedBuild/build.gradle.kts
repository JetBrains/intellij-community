allprojects {
    repositories {
        { { kts_kotlin_plugin_repositories } }
    }
}

plugins {
    kotlin("multiplatform") apply false
    id("com.android.application") apply false
}