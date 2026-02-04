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
        freeCompilerArgs.add("-opt-in=OptInAnnotation")
        languageVersion = {{minimalSupportedKotlinVersion}}
        apiVersion.set({{minimalSupportedKotlinVersion}})
    }
}