plugins {
  id("maven-publish")
  // Java support
  id("java")
  // Kotlin support
  id("org.jetbrains.kotlin.jvm") version "1.8.0"
}

group = "com.jetbrains.intellij.platform"
version = "0.0.6"

repositories {
  mavenCentral()
  maven {
    url = uri("https://www.jetbrains.com/intellij-repository/releases")
  }
  maven {
    url = uri("https://www.jetbrains.com/intellij-repository/nightly")
  }
}

publishing {
  repositories {
    maven {
      url = uri("https://packages.jetbrains.team/maven/p/ide-accessibility-assistant/codegen-test")
      credentials {
        username = System.getProperty("intellij.workspace.codegen.repository.user")
        password = System.getProperty("intellij.workspace.codegen.repository.password")
      }
    }
  }
  publications {
    register("mavenJava", MavenPublication::class) {
      from(components["java"])
    }
  }
}

dependencies {
  implementation("org.jetbrains.kotlin:kotlin-reflect")
  implementation("com.jetbrains.intellij.platform:workspace-model-codegen:LATEST-TRUNK-SNAPSHOT")
  implementation("com.jetbrains.intellij.platform:workspace-model-storage:223.8836.34")
}