import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
}

java.targetCompatibility = JavaVersion.VERSION_11

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        apiVersion = "1.5"
        languageVersion = "1.5"
        jvmTarget = java.targetCompatibility.majorVersion
    }
}

repositories {
    { { kts_kotlin_plugin_repositories } }
}

dependencies {
    implementation(kotlin("stdlib"))
}