plugins {
    kotlin("multiplatform")
    id("com.android.application")
}

repositories {
    {{ kts_kotlin_plugin_repositories }}
}

kotlin {
    android()
    ios()
}
