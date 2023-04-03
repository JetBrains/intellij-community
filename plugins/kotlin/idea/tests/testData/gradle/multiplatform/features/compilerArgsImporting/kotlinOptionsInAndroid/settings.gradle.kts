pluginManagement {
    repositories {
        {{kts_kotlin_plugin_repositories}}
    }
    plugins {
        kotlin("android").version("{{kgp_version}}")
        id("com.android.library") version "{{agp_version}}"
    }
}
