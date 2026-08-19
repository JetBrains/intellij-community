plugins {
    kotlin("multiplatform") apply false
    {{android_library_plugin_id}} apply false
}

allprojects {
    repositories {
        {{kts_kotlin_plugin_repositories}}
    }
}