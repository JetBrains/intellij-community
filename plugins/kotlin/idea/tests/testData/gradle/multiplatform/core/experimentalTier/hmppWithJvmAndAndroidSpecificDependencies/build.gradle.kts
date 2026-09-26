plugins {
    {{android_library_kotlin_plugin_declaration}}
    {{android_library_plugin_id}}
}

{{default_android_block}}

allprojects {
    repositories {
        { { kts_kotlin_plugin_repositories } }
        maven("$rootDir/repo")
    }
}
