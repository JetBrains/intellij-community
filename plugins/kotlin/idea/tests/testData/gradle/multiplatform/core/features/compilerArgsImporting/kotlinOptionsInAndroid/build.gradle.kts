plugins {
    kotlin("android")
    {{android_library_plugin_id}}
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