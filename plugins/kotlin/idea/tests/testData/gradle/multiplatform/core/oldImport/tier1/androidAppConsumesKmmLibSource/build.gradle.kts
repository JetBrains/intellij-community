plugins {
    kotlin("multiplatform") apply false
    id("com.android.library")
}

{{default_android_block}}

allprojects {
    repositories {
        { { kts_kotlin_plugin_repositories } }
    }
}
