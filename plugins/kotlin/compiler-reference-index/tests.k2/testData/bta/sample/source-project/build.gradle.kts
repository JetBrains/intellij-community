plugins {
    kotlin("jvm") version "2.3.20"
    application
}

repositories {
    mavenCentral()
}

application {
    mainClass.set("fixtures.MainKt")
}

kotlin {
    jvmToolchain(17)
}
