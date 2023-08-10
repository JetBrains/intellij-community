plugins {
    kotlin("multiplatform")
}

repositories {
    {{ kts_kotlin_plugin_repositories }}
}

kotlin {
    jvm()
    linuxX64()
}
