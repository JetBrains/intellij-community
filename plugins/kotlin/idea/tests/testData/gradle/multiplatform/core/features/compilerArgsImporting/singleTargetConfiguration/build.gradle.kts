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
            compileTaskProvider.configure {
                compilerOptions.apply {
                    freeCompilerArgs.add("-opt-in=JvmMainOptInAnnotation")
                    languageVersion.set(KotlinVersion.KOTLIN_2_2)
                    apiVersion.set(KotlinVersion.KOTLIN_2_2)
                }
            }
        }

    }

    iosX64 {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions.apply {
                    freeCompilerArgs.add("-opt-in=IosAllOptInAnnotation")
                    languageVersion.set({{nextMinimalSupportedKotlinVersion}})
                    apiVersion.set({{nextMinimalSupportedKotlinVersion}})
                }
            }
        }
    }

    iosArm64()
    js(IR) { nodejs() }

    /* Required to prevent 'org.gradle.api.InvalidUserDataException: Inconsistent settings for Kotlin source sets' */
    metadata().compilations.all {
        compileTaskProvider.configure {
            compilerOptions {
                languageVersion.set({{minimalSupportedKotlinVersion}})
                apiVersion.set({{minimalSupportedKotlinVersion}})
            }
        }
    }
}