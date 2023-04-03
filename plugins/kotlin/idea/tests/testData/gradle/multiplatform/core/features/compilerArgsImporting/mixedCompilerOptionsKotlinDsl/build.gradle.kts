import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

repositories {
    {{kts_kotlin_plugin_repositories}}
}

{{default_android_block}}

kotlin {
    jvm()
    android()
    ios()
    js(IR) { nodejs() }

    targetHierarchy.default {
        group("jvmAndroid") {
            withAndroid()
            withJvm()
        }
    }

    targets.all {
        compilations.all {
            compilerOptions.options.apply {
                freeCompilerArgs.add("-opt-in=CompilationOptInAnnotation")
                languageVersion.set(KotlinVersion.KOTLIN_1_7)
                apiVersion.set(KotlinVersion.KOTLIN_1_7)
            }
        }
    }

    sourceSets.all {
        languageSettings {
            languageVersion = "1.8"
            apiVersion = "1.8"
            progressiveMode = true
            optIn("LangSettingsOptInAnnotation")
        }
    }
}