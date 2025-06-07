import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
}

java.targetCompatibility = JavaVersion.VERSION_11

{{customCodePlaceholder}}

repositories {
    { { kts_kotlin_plugin_repositories } }
}

dependencies {
    implementation(kotlin("stdlib"))
}