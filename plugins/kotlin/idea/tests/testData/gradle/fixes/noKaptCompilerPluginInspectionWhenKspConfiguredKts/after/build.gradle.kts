plugins {
    id("java")
    kotlin("jvm") version "2.4.0"
}

repositories {
    mavenCentral()
}

configurations.create("ksp")

dependencies {
    implementation("org.mapstruct:mapstruct:1.6.3")
    annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")
    add("ksp", "org.mapstruct:mapstruct-processor:1.6.3")
}
