// FIX: Replace 'kotlinOptions' with 'compilerOptions'
// DISABLE_K2_ERRORS
// TODO: KTIJ-32773
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.0.0"
}

repositories {
    mavenCentral()
}

allprojects {
    tasks.withType<KotlinCompile>().configureEach {
        <caret>kotlinOptions.jvmTarget = "1.8"
    }
}
