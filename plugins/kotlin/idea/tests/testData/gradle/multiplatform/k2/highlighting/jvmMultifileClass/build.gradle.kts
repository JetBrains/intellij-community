plugins {
    kotlin("multiplatform") apply false
}

allprojects {
    repositories {
        maven(rootProject.layout.projectDirectory.dir("repo"))
        {{kts_kotlin_plugin_repositories}}
    }

    group = "test"
    version = "1.0"
}
