@file:Suppress("OPT_IN_USAGE")

plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()
    linuxX64()
    linuxArm64()

    targetHierarchy.default()

    sourceSets.commonMain.get().dependencies {
        implementation("org.jetbrains.library:utils:1.0.0")
    }
}