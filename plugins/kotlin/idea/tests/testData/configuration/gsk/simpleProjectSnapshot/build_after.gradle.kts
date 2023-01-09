import org.gradle.api.JavaVersion.VERSION_1_7

plugins {
    application
    kotlin("jvm") version "1.7.255-SNAPSHOT"
}

application {
    mainClassName = "samples.HelloWorld"
}

repositories {
    jcenter()
    maven("https://oss.sonatype.org/content/repositories/snapshots")
}

dependencies {
    testCompile("junit:junit:4.12")
    implementation(kotlin("stdlib-jdk8"))
}

// VERSION: 1.7.255-SNAPSHOT
kotlin {
    jvmToolchain(8)
}
