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
            kotlinOptions.languageVersion = "1.7"
            kotlinOptions.apiVersion = "1.7"
            kotlinOptions.freeCompilerArgs += "-opt-in=OptInAnnotation"
        }
    }
}