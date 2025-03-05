// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project.workspace.util

class MavenPomBuilder private constructor(
  private val coordinates: String,
) {

  private val dependencies = ArrayList<Pair<String, String>>()
  private val properties = ArrayList<Pair<String, String>>()

  fun dependency(scope: String, coordinates: String) {
    dependencies.add(scope to coordinates)
  }

  fun property(name: String, value: String) {
    properties.add(name to value)
  }

  fun generate(): String {
    val propertiesContent = properties.joinToString("\n") { (name, value) ->
      "    <$name>$value</$name>"
    }
    val dependenciesContent = dependencies.joinToString("\n") { (scope, coordinates) ->
      val (groupId, artifactId, version) = coordinates.split(":")
      """
        |    <dependency>
        |      <groupId>$groupId</groupId>
        |      <artifactId>$artifactId</artifactId>
        |      <version>$version</version>
        |      <scope>$scope</scope>
        |    </dependency>
      """.trimMargin()
    }
    val (groupId, artifactId, version) = coordinates.split(":")
    return """
      |<?xml version="1.0" encoding="UTF-8"?>
      |<project xmlns="http://maven.apache.org/POM/4.0.0"
      |         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
      |         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
      |  <modelVersion>4.0.0</modelVersion>
      |
      |  <groupId>$groupId</groupId>
      |  <artifactId>$artifactId</artifactId>
      |  <version>$version</version>
      |
      |  <properties>
      |$propertiesContent
      |  </properties>
      |
      |  <dependencies>
      |$dependenciesContent
      |  </dependencies>
      |</project>
    """.trimMargin()
  }

  companion object {

    fun mavenPom(coordinates: String, configure: MavenPomBuilder.() -> Unit): String {
      return MavenPomBuilder(coordinates).apply(configure).generate()
    }
  }
}