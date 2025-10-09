// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
plugins {
  id("maven-publish")
  id("java")
  id("org.jetbrains.kotlin.jvm") version "2.2.20"
}

fun properties(key: String) = project.findProperty(key).toString()

val codegenImplMinorVersion = properties("codegenImplMinorVersion")
val codegenImplMajorVersion = properties("codegenImplMajorVersion")
val codegenApiVersion = properties("codegenApiVersion")

group = "com.jetbrains.intellij.platform"
version = "$codegenApiVersion.$codegenImplMajorVersion.$codegenImplMinorVersion"

repositories {
  mavenCentral()
  maven {
    url = uri("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
  }
}

kotlin {
  jvmToolchain(21)
}

publishing {
  repositories {
    maven {
      System.getProperty("intellij.dependencies.repo.url")?.let { url = uri(it) }
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
  implementation("com.jetbrains.intellij.platform:workspace-model-codegen:0.0.9")
}


tasks.withType(Jar::class) {
  manifest {
    attributes["Specification-Version"] = codegenApiVersion
    attributes["Implementation-Version"] = codegenImplMajorVersion

    val workspaceModelCodegenVersion = project.configurations["implementation"].allDependencies.matching {
      it.name == "workspace-model-codegen"
    }.first().version

    attributes["Codegen-Api-Version"] = workspaceModelCodegenVersion
  }
}