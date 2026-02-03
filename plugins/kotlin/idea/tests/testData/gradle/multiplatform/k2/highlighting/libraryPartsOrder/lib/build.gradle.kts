@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    kotlin("multiplatform")
    id("maven-publish")
}

kotlin {
    linuxX64("lin1")
    linuxArm64("lin2")
    macosX64("mac1")
    macosArm64("mac2")

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

publishing {
    repositories {
        maven(rootProject.layout.projectDirectory.dir("repo"))
    }
}
