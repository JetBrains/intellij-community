import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    kotlin("jvm")
}

repositories {
    {{kts_kotlin_plugin_repositories}}
}

kotlin {
    compilerOptions {
        apiVersion.set({{minimalSupportedKotlinVersion}})
        languageVersion.set({{minimalSupportedKotlinVersion}})
        freeCompilerArgs.add("-opt-in=OptInAnnotation")
    }
}