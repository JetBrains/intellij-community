plugins {
    kotlin("multiplatform")
}

repositories {
    maven(file(rootDir.resolve("repo")))
    { { kts_kotlin_plugin_repositories } }
}

kotlin {
    jvmToolchain(17)
    jvm()

    sourceSets.commonMain.dependencies {
        implementation("org.jetbrains.sample:producer:1.0.0-SNAPSHOT")
    }
}
