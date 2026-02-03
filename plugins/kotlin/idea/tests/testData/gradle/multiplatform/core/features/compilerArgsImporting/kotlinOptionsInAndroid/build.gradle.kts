plugins {
    kotlin("android")
    id("com.android.library")
}

repositories {
    {{kts_kotlin_plugin_repositories}}
}

{{default_android_block}}

android {
    kotlinOptions {
        freeCompilerArgs += "-opt-in=OptInAnnotation"
        languageVersion = "1.7"
        apiVersion = "1.7"
    }
}