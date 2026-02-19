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
}

tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask::class.java).all {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=OptInAnnotation")
        languageVersion.set({{minimalSupportedKotlinVersion}})
        apiVersion.set({{minimalSupportedKotlinVersion}})
    }
}