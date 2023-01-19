plugins {
    kotlin("multiplatform")
}

kotlin {
    js(IR) {
        nodejs()
    }
}

repositories {
    { { kts_kotlin_plugin_repositories } }
}

