plugins {
    kotlin("multiplatform") apply false
    id("com.android.library") apply false
}

allprojects {
    repositories {
        {{kts_kotlin_plugin_repositories}}
    }
}