// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project.workspace.util

class MavenSettingsBuilder private constructor() {

  private val properties = ArrayList<Pair<String, String>>()

  fun property(name: String, value: String) {
    properties.add(name to value)
  }

  fun generate(): String {
    val propertiesContent = properties.joinToString("\n") { (name, value) ->
      "  <$name>$value</$name>"
    }
    return """
      |<?xml version="1.0" encoding="UTF-8"?>
      |<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
      |          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
      |          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">
      |$propertiesContent
      |</settings>
    """.trimMargin()
  }

  companion object {

    fun mavenSettings(configure: MavenSettingsBuilder.() -> Unit): String {
      return MavenSettingsBuilder().apply(configure).generate()
    }
  }
}