@file:Suppress("OPT_IN_USAGE")

plugins {
    kotlin("multiplatform")
    `maven-publish`
}


group = "org.jetbrains.sample"
version = "1.0.0-SNAPSHOT"

kotlin {
    jvm()
    linuxX64()
    linuxArm64()
}