plugins {
    kotlin("jvm")
    id("maven-publish")
}

group = "org.jetbrains.kotlin.mpp.tests"
version = "1.0"

publishing {
    repositories {
        maven("$rootDir/repo")
    }

    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
