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
  maven {
    url = uri("https://www.jetbrains.com/intellij-repository/releases")
  }
}

dependencies {
  implementation("org.jetbrains:annotations:26.1.0")
  testImplementation(platform("org.junit:junit-bom:6.0.3"))
  testImplementation("org.junit.jupiter:junit-jupiter")
  testImplementation("org.junit.jupiter:junit-jupiter-params")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
  testImplementation("org.eclipse.jdt:ecj:3.31.0")
  testImplementation("org.codehaus.groovy:groovy-all:3.0.25")
  testImplementation("com.jetbrains.intellij.java:java-rt:253.31033.145") // Make it possible to use FileComparisonData
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