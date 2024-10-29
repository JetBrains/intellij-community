// FIX: Replace 'kotlinOptions' with 'compilerOptions'
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

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

val javaVersion = JavaVersion.VERSION_1_8
tasks.withType(KotlinJvmCompile::class.java).configureEach {
    <caret>kotlinOptions {
        jvmTarget = javaVersion.toString()
        freeCompilerArgs += setOf(
            "-Xjvm-default=all",
        )
    }
}