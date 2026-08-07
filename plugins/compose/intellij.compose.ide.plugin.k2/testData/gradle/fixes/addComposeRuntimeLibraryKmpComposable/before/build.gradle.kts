buildscript {
    repositories {
        {{kts_kotlin_plugin_repositories}}
    }

    dependencies {
        classpath("org.jetbrains.compose:compose-gradle-plugin:1.11.0")
    }
}

plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()
    linuxX64()
}

repositories {
    {{kts_kotlin_plugin_repositories}}
}