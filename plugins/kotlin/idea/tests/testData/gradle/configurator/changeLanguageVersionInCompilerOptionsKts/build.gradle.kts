import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
   kotlin("jvm") version "{{kotlin_plugin_version}}"
}

val compileKotlin: KotlinCompile by tasks

compileKotlin.compilerOptions {
    languageVersion = KotlinVersion.KOTLIN_1_8
}