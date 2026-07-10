plugins {
    id("java")
    kotlin("jvm") version "2.4.0"
    kotlin("kapt") version "2.4.0"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.mapstruct:mapstruct:1.6.3")
    annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")
    kapt("org.mapstruct:mapstruct-processor:1.6.3")
    testImplementation(kotlin("test"))
}
