// FIX: Replace 'kotlinOptions' with 'compilerOptions'
// DISABLE_K2_ERRORS
// TODO: KTIJ-32773
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_1_8
    }

    <caret>kotlinOptions {
        if (overriddenLanguageVersion != null) {
            languageVersion = overriddenLanguageVersion
            // We replace statements even in children
            freeCompilerArgs += "-Xsuppress-version-warnings"
        }
    }
}