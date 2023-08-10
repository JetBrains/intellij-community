import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.plugin.KotlinHierarchyTemplate

plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

{{default_android_block}}

repositories {
    {{kts_kotlin_plugin_repositories}}
}

kotlin {
    jvm()
    android()
    ios()
    js(IR) { nodejs() }

    applyHierarchyTemplate(KotlinHierarchyTemplate.default) {
        common {
            group("jvmAndroid") {
                withAndroid()
                withJvm()
            }
        }
    }

    targets.all {
        compilations.all {
            compilerOptions.options.apply {
                freeCompilerArgs.add("-opt-in=OptInAnnotation")
                languageVersion.set(KotlinVersion.KOTLIN_1_7)
                apiVersion.set(KotlinVersion.KOTLIN_1_7)
            }
        }
    }
}