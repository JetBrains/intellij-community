plugins {
  id("maven-publish")
  id("java")
  id("org.jetbrains.kotlin.jvm") version "1.8.0"
}

val codegenImplMinorVersion =  project.findProperty("codegenImplMinorVersion")?.toString()
val codegenImplMajorVersion =  project.findProperty("codegenImplMajorVersion")?.toString()
val codegenApiVersion =  project.findProperty("codegenApiVersion")?.toString()

group = "com.jetbrains.intellij.platform"
version = "$codegenApiVersion.$codegenImplMajorVersion.$codegenImplMinorVersion"

repositories {
  mavenCentral()
  maven {
    url = uri("https://www.jetbrains.com/intellij-repository/releases")
  }
  maven {
    url = uri("https://www.jetbrains.com/intellij-repository/nightly")
  }
  maven {
    url = uri("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
  }
}

kotlin {
  jvmToolchain(17)
}

tasks.withType(Jar::class) {
  manifest {
    attributes["Specification-Version"] = "$codegenApiVersion"
    attributes["Implementation-Version"] = "$codegenImplMajorVersion"
  }
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
  implementation("org.jetbrains.kotlin:kotlin-reflect")
  implementation("com.jetbrains.intellij.platform:workspace-model-codegen:0.0.2")
  implementation("com.jetbrains.intellij.platform:workspace-storage:LATEST-TRUNK-SNAPSHOT")
  implementation("com.jetbrains.intellij.platform:workspace-jps:LATEST-TRUNK-SNAPSHOT")
}