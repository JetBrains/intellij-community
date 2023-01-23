pluginManagement {
    repositories {
        { { kts_kotlin_plugin_repositories } }
    }

    plugins {
        kotlin("jvm") version "{{kotlin_plugin_version}}"
    }
}

include(":consumerA")
includeBuild("../producerBuild")