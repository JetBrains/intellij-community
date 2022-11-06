plugins {
    kotlin("multiplatform")
}

repositories {
    {{kts_kotlin_plugin_repositories}}
}

kotlin {
    linuxX64()
    macosX64()
}
