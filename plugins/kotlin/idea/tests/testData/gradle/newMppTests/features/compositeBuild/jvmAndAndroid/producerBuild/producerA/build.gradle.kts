@file:Suppress("OPT_IN_USAGE")

plugins {
    kotlin("multiplatform")
    id("com.android.library")
    `maven-publish`
}

group = "org.jetbrains.sample"
version = "1.0.0-SNAPSHOT"

{{default_android_block}}

kotlin {
    jvm()
    android()
}
