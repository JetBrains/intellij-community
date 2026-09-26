plugins {
    kotlin("jvm") version "2.3.0"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":lib"))
    implementation(kotlin("reflect"))
}
