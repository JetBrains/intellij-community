plugins {
  id("maven-publish")
  id("java")
  id("org.jetbrains.kotlin.jvm") version "1.8.0"
}

group = "com.jetbrains.intellij.platform"
version = "0.0.9"

repositories {
  mavenCentral()
  maven {
    url = uri("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
  }
}

kotlin {
  jvmToolchain(17)
}

tasks.withType(Jar::class) {
  val resources = sourceSets.main.get().resources
  File("${resources.srcDirs.first().path}/codegen-api-metadata.json")
    .writeText("{ \"Codegen-Api-Version\": \"$version\" }")
}

publishing {
  repositories {
    maven {
      url = uri(System.getProperty("intellij.dependencies.repo.url") ?: "")
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
  implementation("org.jetbrains:annotations:24.0.0")
}