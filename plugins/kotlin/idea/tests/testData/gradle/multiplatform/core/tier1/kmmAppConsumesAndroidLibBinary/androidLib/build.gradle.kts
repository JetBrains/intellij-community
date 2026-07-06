plugins {
    {{android_library_plugin_id}}
    {{android_library_kotlin_plugin}}
    `maven-publish`
}

group = "org.jetbrains.kotlin.mpp.tests"
version = "1.0"

{{default_android_block}}

publishing {
    repositories {
        maven("$rootDir/repo")
    }
    {{android_library_manual_publication_snippet}}
}
