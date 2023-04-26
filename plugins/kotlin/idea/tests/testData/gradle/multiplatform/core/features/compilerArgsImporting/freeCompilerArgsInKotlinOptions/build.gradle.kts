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
            kotlinOptions.freeCompilerArgs += "-language-version=1.7"
            kotlinOptions.freeCompilerArgs += "-api-version=1.7"
            kotlinOptions.freeCompilerArgs += "-opt-in=OptInAnnotation"
        }
    }
}