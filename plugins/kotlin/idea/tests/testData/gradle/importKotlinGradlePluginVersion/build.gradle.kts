plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()
    linuxX64()
}

repositories {
    { { kts_kotlin_plugin_repositories } }
}

