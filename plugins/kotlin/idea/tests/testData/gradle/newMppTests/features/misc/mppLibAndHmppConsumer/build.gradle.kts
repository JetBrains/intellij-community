repositories {
    run {
        val publishedLibRepoPath = "$rootDir/lib/repo"
        if (!file(publishedLibRepoPath).isDirectory) {
            logger.error(
                "\nThis module needs the lib from `lib-and-app` to be published to $publishedLibRepoPath." +
                        "\nPlease run the `publish` task in the `lib-and-app` project."
            )
        }
        maven(publishedLibRepoPath)
    }

    {{kts_kotlin_plugin_repositories}}
}

plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

android {
    compileSdkVersion({{compile_sdk_version}})
    buildToolsVersion("{{build_tools_version}}")
    namespace = "org.jetbrains.kotlin.mpp.tests"
}

kotlin {
    android()
    ios()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("com.h0tk3y.mpp.demo:lib:1.0")
            }
        }
    }
}
