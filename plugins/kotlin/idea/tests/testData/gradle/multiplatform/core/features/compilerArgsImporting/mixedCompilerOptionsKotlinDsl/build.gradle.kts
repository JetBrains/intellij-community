import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.plugin.KotlinHierarchyTemplate

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
    {{androidTargetPlaceholder}}
    {{iosTargetPlaceholder}}
    js(IR) { nodejs() }

    applyHierarchyTemplate(KotlinHierarchyTemplate.default) {
        common {
            group("jvmAndroid") {
                withAndroidTarget()
                withJvm()
            }
        }
    }

    targets.all {
        compilations.all {
            compilerOptions.options.apply {
                freeCompilerArgs.add("-opt-in=CompilationOptInAnnotation")
                languageVersion.set(KotlinVersion.KOTLIN_1_9)
                apiVersion.set(KotlinVersion.KOTLIN_1_9)
            }
        }
    }

    sourceSets.all {
        languageSettings {
            languageVersion = "2.0"
            apiVersion = "2.0"
            progressiveMode = true
            optIn("LangSettingsOptInAnnotation")
        }
    }
}