rootProject.name = "full-line"

pluginManagement {
    @Suppress("UnstableApiUsage")
    plugins {
        val kotlinVersion: String by settings
        val intellijGradleVersion: String by settings
        val changelogVersion: String by settings

        java
        kotlin("jvm") version kotlinVersion
        id("org.jetbrains.intellij") version intellijGradleVersion
        id("org.jetbrains.changelog") version changelogVersion
        id("org.jetbrains.kotlin.plugin.serialization") version kotlinVersion
    }
}

includeBuild("../markers")

include("languages:common")
include("languages:java")
include("languages:kotlin")
include("languages:python")
include("languages:js")
