import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
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

    targets.all {
        compilations.all {
            compilerOptions.options.apply {
                freeCompilerArgs.add("-opt-in=CompilationOptInAnnotation")
                languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9)
                apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9)
            }
        }
    }

    applyHierarchyTemplate(KotlinHierarchyTemplate.default) {
        common {
            group("jvmAndroid") {
                withAndroidTarget()
                withJvm()
            }
        }
    }
}

// How is this test any different from previous tests??
tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions {
        languageVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0
        apiVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_8
        freeCompilerArgs.add("-opt-in=JvmCompileOptInAnnotation")
    }
}