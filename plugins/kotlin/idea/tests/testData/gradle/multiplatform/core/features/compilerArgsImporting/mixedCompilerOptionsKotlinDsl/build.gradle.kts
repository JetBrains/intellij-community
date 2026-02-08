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
                languageVersion.set({{minimalSupportedKotlinVersion}})
                apiVersion.set({{minimalSupportedKotlinVersion}})
            }
        }
    }

    sourceSets.all {
        languageSettings {
            languageVersion = "{{nextMinimalSupportedKotlinLanguageVersion}}"
            apiVersion = "{{nextMinimalSupportedKotlinLanguageVersion}}"
            progressiveMode = true
            optIn("LangSettingsOptInAnnotation")
        }
    }
}