plugins {
    kotlin("multiplatform")
    {{android_library_plugin_id}}
    id("maven-publish")
}

group = "org.jetbrains.kotlin.mpp.tests"
version = "1.0"

publishing {
    repositories {
        maven("$rootDir/repo")
    }
}

{{default_android_block}}

kotlin {
    {{iosTargetPlaceHolder}}
    {{androidTargetPlaceholder}}
    {{android_target_publishing_snippet}}

    sourceSets {
        val androidMain by getting {
            kotlin.srcDir("src/main/kotlin")
        }
    }
}
