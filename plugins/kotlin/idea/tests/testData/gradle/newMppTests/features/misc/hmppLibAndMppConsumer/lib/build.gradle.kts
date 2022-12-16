plugins {
    kotlin("multiplatform")
    id("maven-publish")
    id("com.android.library")
}

group = "com.h0tk3y.mpp.demo"
version = "1.0"

repositories {
    {{kts_kotlin_plugin_repositories}}
}

android {
    compileSdkVersion({{compile_sdk_version}})
    buildToolsVersion("{{build_tools_version}}")
    namespace = "org.jetbrains.kotlin.mpp.tests"
}

kotlin {
    android {
        publishLibraryVariants("release", "debug")
    }
    ios()
}

publishing {
    repositories {
        maven("$rootDir/repo")
    }
}
