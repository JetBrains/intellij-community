pluginManagement {
    repositories {
        {{kts_kotlin_plugin_repositories}}
    }
}
dependencyResolutionManagement {
    repositories {
        {{kts_kotlin_plugin_repositories}}
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
