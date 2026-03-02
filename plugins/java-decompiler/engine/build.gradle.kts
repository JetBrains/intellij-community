// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
plugins {
  java
  application
}

tasks.compileJava {
  sourceCompatibility = "21"
  targetCompatibility = "21"
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
  implementation("org.jetbrains:annotations:26.1.0")
  testImplementation(platform("org.junit:junit-bom:6.0.3"))
  testImplementation("org.junit.jupiter:junit-jupiter")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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
  useJUnitPlatform()
  maxHeapSize = "1024m"
}