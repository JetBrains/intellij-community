pluginManagement {
    repositories {
        { { kts_kotlin_plugin_repositories } }
    }
    plugins {
        kotlin("{{kpm_plugin_name}}").version("{{kotlin_plugin_version}}")
    }
}

include("lib", "app-with-project-dep")
