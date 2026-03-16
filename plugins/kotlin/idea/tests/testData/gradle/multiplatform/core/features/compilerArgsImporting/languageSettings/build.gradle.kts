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
        group("jvmAndroid") {
            withAndroidTarget()
            withJvm()
        }
    }


    sourceSets.all {
        languageSettings {
            languageVersion = "1.7"
            apiVersion = "1.7"
            progressiveMode = true
            optIn("OptInAnnotation")
        }
    }
}