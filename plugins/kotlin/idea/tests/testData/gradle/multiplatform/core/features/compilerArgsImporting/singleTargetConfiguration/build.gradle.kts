import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    kotlin("multiplatform")
}

repositories {
    { { kts_kotlin_plugin_repositories } }
}

kotlin {
    jvm {
        withJava()
        compilations.getByName("main") {
            compilerOptions.options.apply {
                freeCompilerArgs.add("-opt-in=JvmMainOptInAnnotation")
                languageVersion.set(KotlinVersion.KOTLIN_1_9)
                apiVersion.set(KotlinVersion.KOTLIN_1_9)
            }
        }

    }

    iosX64 {
        compilations.all {
            compilerOptions.options.apply {
                freeCompilerArgs.add("-opt-in=IosAllOptInAnnotation")
                languageVersion.set(KotlinVersion.KOTLIN_1_8)
                apiVersion.set(KotlinVersion.KOTLIN_1_8)
            }
        }
    }

    iosArm64()
    js(IR) { nodejs() }

    /* Required to prevent 'org.gradle.api.InvalidUserDataException: Inconsistent settings for Kotlin source sets' */
    metadata().compilations.all {
        compilerOptions {
            languageVersion.set(KotlinVersion.KOTLIN_1_7)
            apiVersion.set(KotlinVersion.KOTLIN_1_7)
        }
    }
}