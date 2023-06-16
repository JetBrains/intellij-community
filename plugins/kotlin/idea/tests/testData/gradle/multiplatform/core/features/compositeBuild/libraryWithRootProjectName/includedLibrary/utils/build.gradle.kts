@file:Suppress("OPT_IN_USAGE")

plugins {
    kotlin("multiplatform")
    `maven-publish`
}

group = "org.jetbrains.library"

kotlin {
    jvm()
    linuxX64()
    linuxArm64()
    targetHierarchy.default()

    sourceSets.commonMain.get().dependencies {
        api(project(":"))
    }
}