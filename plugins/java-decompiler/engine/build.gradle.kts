// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
plugins {
    java
}

tasks.withType<JavaCompile> {
    options.release = 17
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains:annotations:24.0.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.assertj:assertj-core:3.26.3")
}

tasks.named<Jar>("jar") {
    archiveBaseName = "fernflower"

    manifest {
        attributes["Main-Class"] = "org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler"
    }
}

tasks.named<Test>("test") {
    maxHeapSize = "1024m"
}
