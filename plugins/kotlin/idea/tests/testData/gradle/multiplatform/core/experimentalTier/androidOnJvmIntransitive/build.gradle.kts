plugins {
    kotlin("multiplatform") apply false
    {{android_root_plugins_apply_false}}
}

allprojects {
    repositories {
        { { kts_kotlin_plugin_repositories } }
    }
}
