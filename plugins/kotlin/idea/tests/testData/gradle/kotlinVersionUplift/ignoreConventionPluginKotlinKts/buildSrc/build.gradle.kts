plugins {
    `kotlin-dsl`
    kotlin("jvm") version "1.8.20"
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.8.22")
}