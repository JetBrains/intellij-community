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
    // We don't support the target 1.7 and thus don't convert it
    jvmTarget = "1.7"
}
