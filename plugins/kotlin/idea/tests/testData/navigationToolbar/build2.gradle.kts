// NAV_BAR_ITEMS: src, build2.gradle.kts, someFun

plugins {
    kotlin("jvm") version "1.7.10"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
}

fun someFun() {
    TODO()<caret>
}