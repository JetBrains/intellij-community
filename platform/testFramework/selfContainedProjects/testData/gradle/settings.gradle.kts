pluginManagement {
  repositories {
    maven("https://cache-redirector.jetbrains.com/maven-central")
    maven("https://cache-redirector.jetbrains.com/plugins.gradle.org")
  }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "ktor-sample"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(":subproject1")
includeBuild("subbuild1")