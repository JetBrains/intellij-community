import org.gradle.api.JavaVersion.VERSION_1_7
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    kotlin("jvm") version "1.6.20-dev-456"
}

application {
    mainClassName = "samples.HelloWorld"
}

repositories {
    jcenter()
    maven("https://teamcity.jetbrains.com/guestAuth/app/rest/builds/buildType:(id:Kotlin_KotlinPublic_Aggregate),number:1.6.20-dev-456,branch:(default:any)/artifacts/content/maven")
}

dependencies {
    testCompile("junit:junit:4.12")
    implementation(kotlin("stdlib-jdk8"))
}

// VERSION: 1.6.20-dev-456
val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "1.8"
}
val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "1.8"
}
