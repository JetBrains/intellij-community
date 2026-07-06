plugins {
    kotlin("multiplatform")
    id("maven-publish")
    {{android_library_plugin_id}}
}

group = "com.h0tk3y.mpp.demo"
version = "1.0"

repositories {
    {{kts_kotlin_plugin_repositories}}
}

{{default_android_block}}

kotlin {
    {{androidTargetPlaceholder}}
    {{android_target_publishing_snippet}}
    {{iosTargetPlaceholder}}
}

publishing {
    repositories {
        maven("$rootDir/repo")
    }
}
