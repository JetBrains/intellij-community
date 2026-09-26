buildscript {
    repositories {
        {{kts_kotlin_plugin_repositories}}
    }
    dependencies {
        classpath("org.jetbrains.compose:compose-gradle-plugin:1.11.0")
    }
}

plugins {
    kotlin("jvm")
}

repositories {
    {{kts_kotlin_plugin_repositories}}
}
dependencies {
    implementation("org.jetbrains.compose.runtime:runtime:1.11.0")
}