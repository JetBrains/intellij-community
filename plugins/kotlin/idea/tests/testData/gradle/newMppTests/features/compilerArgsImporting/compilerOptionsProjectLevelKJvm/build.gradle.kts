import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    kotlin("jvm")
}

repositories {
    {{kts_kotlin_plugin_repositories}}
}

kotlin {
    compilerOptions {
        apiVersion.set(KotlinVersion.KOTLIN_1_7)
        languageVersion.set(KotlinVersion.KOTLIN_1_7)
        freeCompilerArgs.add("-opt-in=OptInAnnotation")
    }
}