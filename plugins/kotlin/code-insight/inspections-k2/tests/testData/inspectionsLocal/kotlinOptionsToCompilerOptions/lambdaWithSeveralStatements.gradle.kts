// FIX: Replace 'kotlinOptions' with 'compilerOptions'
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.0.0"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.<caret>kotlinOptions {
    jvmTarget = "9"
    freeCompilerArgs += listOf("-module-name", "TheName")
    apiVersion = "1.9"
}