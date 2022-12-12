pluginManagement {
    repositories {
        {{kts_kotlin_plugin_repositories}}
    }
    plugins {
        kotlin("multiplatform") version "{{kotlin_plugin_version}}"
    }
}

dependencyResolutionManagement {
    repositories {
        {{kts_kotlin_plugin_repositories}}
    }
}

include(":consumerA")

includeBuild("../producerBuild") {
    dependencySubstitution {
        substitute(module("org.jetbrains.sample:producerA")).using(project(":producerA"))
    }
}
