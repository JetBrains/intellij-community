import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

plugins {
    kotlin("multiplatform") version "{{kotlin_plugin_version}}"
}

allprojects {
    repositories {
        { { kts_kotlin_plugin_repositories } }
    }
}

kotlin {
    jvm()
}