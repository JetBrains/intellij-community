allprojects {
    repositories {
        { { kts_kotlin_plugin_repositories } }
    }
}

plugins {
    kotlin("multiplatform") apply false
    {{android_application_compatible_plugin_id}} apply false
}