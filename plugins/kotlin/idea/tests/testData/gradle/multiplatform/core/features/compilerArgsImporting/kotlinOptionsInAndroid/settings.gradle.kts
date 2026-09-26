pluginManagement {
    repositories {
        {{kts_kotlin_plugin_repositories}}
    }
    plugins {
        kotlin("android").version("{{kgp_version}}")
        {{android_library_plugin_id}} version "{{agp_version}}"
    }
}
