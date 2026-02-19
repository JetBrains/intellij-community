import org.gradle.api.JavaVersion.VERSION_1_7

plugins {
    application
    kotlin("jvm") version "1.6.20"
}

apply {
    plugin("kotlin")
}

application {
    mainClassName = "samples.HelloWorld"
}

repositories {
    jcenter()
}

dependencies {
    testCompile("junit:junit:4.12")
    implementation(kotlin("stdlib-jdk8"))
}

// VERSION: 1.6.20
kotlin {
    jvmToolchain {
        (this as JavaToolchainSpec).languageVersion.set(JavaLanguageVersion.of(8))
    }
}
