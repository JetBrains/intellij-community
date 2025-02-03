package buildsrc.convention
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    kotlin("jvm")
}

extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
    sourceSets.configureEach {
        languageSettings.optIn("kotlin.RequiresOptIn")
    }
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-receivers")
        suppressWarnings.set(true)
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}