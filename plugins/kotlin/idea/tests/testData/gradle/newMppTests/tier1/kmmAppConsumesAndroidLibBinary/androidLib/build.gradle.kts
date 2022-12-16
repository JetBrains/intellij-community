plugins {
    id("com.android.library")
    kotlin("android")
    `maven-publish`
}

group = "org.jetbrains.kotlin.mpp.tests"
version = "1.0"

android {
    compileSdkVersion({{compile_sdk_version}})
    buildToolsVersion("{{build_tools_version}}")
    namespace = "org.jetbrains.kotlin.mpp.tests"
}

publishing {
    repositories {
        maven("$rootDir/repo")
    }

    afterEvaluate {
        publications {
            create<MavenPublication>("default") {
                from(components["release"])
            }
        }
    }
}
