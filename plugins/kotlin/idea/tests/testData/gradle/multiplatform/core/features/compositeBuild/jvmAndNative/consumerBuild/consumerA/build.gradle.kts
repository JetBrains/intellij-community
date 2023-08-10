@file:Suppress("OPT_IN_USAGE")

plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()
    linuxX64()
    linuxArm64()

    sourceSets.commonMain.dependencies {
        implementation("org.jetbrains.sample:producerA:1.0.0-SNAPSHOT")
    }
}