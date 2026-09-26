// NAV_BAR_ITEMS: src, build1.gradle.kts, repositories

plugins {
    kotlin("jvm") version "1.7.10"
    application
}

repositories {
    mavenCentral()<caret>
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
}