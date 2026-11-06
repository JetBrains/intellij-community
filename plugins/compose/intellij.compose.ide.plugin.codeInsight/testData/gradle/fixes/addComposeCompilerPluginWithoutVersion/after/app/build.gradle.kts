plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlin.plugin.compose")
}

repositories {
    {{kts_kotlin_plugin_repositories}}
}

dependencies {
    implementation("org.jetbrains.compose.runtime:runtime:1.11.0")
}
