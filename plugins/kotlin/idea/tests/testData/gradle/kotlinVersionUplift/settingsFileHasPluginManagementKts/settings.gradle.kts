pluginManagement {
    plugins {
        kotlin("multiplatform") version "1.8.21"
    }
    repositories {
        jcenter()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven")
    }
}
rootProject.name = "project"