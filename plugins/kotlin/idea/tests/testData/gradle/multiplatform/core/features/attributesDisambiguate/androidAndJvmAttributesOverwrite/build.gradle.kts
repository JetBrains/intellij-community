allprojects {
    repositories {
        { { kts_kotlin_plugin_repositories } }
    }
}

plugins {
    kotlin("multiplatform") apply false
    {{android_root_plugins_apply_false}}
}
