plugins {
  // Java support
  id("java")
  // Kotlin support
  id("org.jetbrains.kotlin.jvm") version "1.8.0"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
  mavenCentral()
  maven {
    url = uri("https://www.jetbrains.com/intellij-repository/releases")
  }
}

dependencies {
  implementation("com.jetbrains.intellij.platform:workspace-model-storage:223.8836.34")
}