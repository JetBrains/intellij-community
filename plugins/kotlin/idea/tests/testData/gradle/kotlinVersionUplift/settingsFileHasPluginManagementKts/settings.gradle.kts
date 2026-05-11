pluginManagement {
    plugins {
        kotlin("multiplatform") version "2.2.20"
    }
    repositories {
        jcenter()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven")
    }
}
rootProject.name = "project"