import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
}

repositories {
    {{kts_kotlin_plugin_repositories}}
}

tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget("11")
        languageVersion = {{minimalSupportedKotlinVersion}}
        apiVersion.set({{minimalSupportedKotlinVersion}})
        freeCompilerArgs.add("-opt-in=OptInAnnotation")
    }
}