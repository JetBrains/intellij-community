plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    kotlin("plugin.noarg")
    kotlin("plugin.lombok")
    kotlin("plugin.assignment")
    kotlin("plugin.power-assert")
}

repositories {
    { { kts_kotlin_plugin_repositories } }
}

kotlin {
    jvm()
    linuxX64()
    linuxArm64()

    sourceSets.commonMain.dependencies {
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.0")
    }
}
