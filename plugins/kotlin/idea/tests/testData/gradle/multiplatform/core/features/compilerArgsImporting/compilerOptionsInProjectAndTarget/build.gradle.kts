import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.plugin.KotlinHierarchyTemplate

plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

repositories {
    { { kts_kotlin_plugin_repositories } }
}

{ { default_android_block } }

kotlin {

    compilerOptions {
        apiVersion.set(KotlinVersion.KOTLIN_1_7)
        languageVersion.set(KotlinVersion.KOTLIN_1_7)
        optIn.add("project.Foo")
    }

    jvm {
        compilerOptions {
            optIn.add("jvm.Foo")
        }
    }

    android()
    js(IR) { nodejs() }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { target ->
        target.compilerOptions {
            apiVersion.set(KotlinVersion.KOTLIN_1_8)
            languageVersion.set(KotlinVersion.KOTLIN_1_8)
            optIn.add("project.ios")
        }
    }

    applyHierarchyTemplate(KotlinHierarchyTemplate.default) {
        common {
            group("jvmAndroid") {
                withAndroid()
                withJvm()
            }
        }
    }
}