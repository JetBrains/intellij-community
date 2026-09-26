plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

repositories {
    {{kts_kotlin_plugin_repositories}}
}

android {
    namespace = "org.jetbrains.kotlin.gradle.idea.importing.android.fix.test"
    compileSdkVersion({{compile_sdk_version}})
    buildToolsVersion("{{build_tools_version}}")
}

kotlin {
    {{androidTargetPlaceholder}}
    jvm()
}
