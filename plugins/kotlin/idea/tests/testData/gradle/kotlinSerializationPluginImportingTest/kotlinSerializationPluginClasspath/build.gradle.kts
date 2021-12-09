plugins {
    kotlin("jvm") version "{{kotlin_plugin_version}}"
    kotlin("plugin.serialization") version "{{kotlin_plugin_version}}"
}

repositories {
    { { kts_kotlin_plugin_repositories } }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.1")
}