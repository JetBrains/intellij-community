pluginManagement {
    repositories {
        {{kts_kotlin_plugin_repositories}}
    }
    plugins {
        kotlin("jvm") version "{{kgp_version}}"
    }
}
dependencyResolutionManagement {
    repositories {
        {{kts_kotlin_plugin_repositories}}
    }
}

rootProject.name = "KTIJ29619KotlinJvmWithTestFixtures"