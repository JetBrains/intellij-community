import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    kotlin("jvm")
}

repositories {
    {{kts_kotlin_plugin_repositories}}
}

tasks.withType<KotlinJvmCompile>().configureEach {
    kotlinOptions.jvmTarget = "11"
    kotlinOptions.languageVersion = "1.7"
    kotlinOptions.freeCompilerArgs += "-opt-in=OptInAnnotation"
    compilerOptions {
        apiVersion.set(KotlinVersion.KOTLIN_1_7)
    }
}