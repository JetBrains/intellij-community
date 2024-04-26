plugins {
    kotlin("jvm")
    `maven-publish`
}

group = "org.jetbrains.sample"
version = "1.0.0-SNAPSHOT"

publishing {
    repositories {
        maven(file(rootDir.resolve("repo")))
    }

    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

repositories {
    { { kts_kotlin_plugin_repositories } }
}

kotlin {
    jvmToolchain(11)
}
