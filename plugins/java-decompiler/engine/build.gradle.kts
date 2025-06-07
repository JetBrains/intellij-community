// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
plugins {
  java
  application
}

tasks.compileJava {
  sourceCompatibility = "17"
  targetCompatibility = "17"
}

sourceSets {
  main {
    java.srcDirs("src")
  }
  test {
    java.srcDirs("test")
  }
}

repositories {
  mavenCentral()
}

dependencies {
  implementation("org.jetbrains:annotations:24.0.0")
  testImplementation("junit:junit:4.13.2")
  testImplementation("org.assertj:assertj-core:3.26.3")
}

application {
  mainClass = "org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler"
}

tasks.withType<CreateStartScripts> {
  applicationName = "fernflower"
}

tasks.jar {
  archiveFileName = "fernflower.jar"
  manifest {
    attributes["Main-Class"] = "org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler"
  }
}

tasks.test {
  maxHeapSize = "1024m"
}