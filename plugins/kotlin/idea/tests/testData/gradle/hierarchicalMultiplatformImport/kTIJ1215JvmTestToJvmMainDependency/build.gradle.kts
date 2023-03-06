plugins {
    kotlin("multiplatform") apply false
    id("com.android.library")
}

allprojects {
    repositories {
        {{kts_kotlin_plugin_repositories}}
    }
}
