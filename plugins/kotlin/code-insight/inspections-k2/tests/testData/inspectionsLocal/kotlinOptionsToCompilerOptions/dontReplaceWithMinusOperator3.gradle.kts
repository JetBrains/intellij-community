// PROBLEM: none

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.0.0"
}

repositories {
    mavenCentral()
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    dependencies {
        implementation(kotlin("stdlib-jdk8"))
    }

    tasks.withType<KotlinCompile>().all {
        <caret>kotlinOptions.freeCompilerArgs = freeCompilerArgs - "-Xopt-in=kotlin.RequiresOptIn" + "-opt-in=androidx.compose.animation.ExperimentalAnimationApi"
    }
}
