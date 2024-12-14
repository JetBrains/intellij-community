import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
}

val compileTestKotlin: KotlinCompile by tasks

compileTestKotlin.compilerOptions {
    languageVersion.set(KotlinVersion.KOTLIN_2_1)
    freeCompilerArgs.set(listOf("-Xmulti-dollar-interpolation"))
}
