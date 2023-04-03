plugins {
    kotlin("multiplatform")
    id("com.android.library")
}


repositories {
    {{kts_kotlin_plugin_repositories}}
}

{{default_android_block}}

kotlin {
    android()
}
