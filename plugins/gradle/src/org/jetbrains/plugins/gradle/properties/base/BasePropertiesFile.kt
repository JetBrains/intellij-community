// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.properties.base

import com.intellij.openapi.project.Project
import com.intellij.util.io.exists
import com.intellij.util.io.inputStream
import com.intellij.util.io.isFile
import java.io.IOException
import java.nio.file.Path
import java.util.Properties

abstract class BasePropertiesFile<PROPERTIES> {

  abstract val propertiesFileName: String

  abstract fun getProperties(project: Project, externalProjectPath: Path): PROPERTIES

  protected fun loadProperties(propertiesFile: Path): Properties? {
    if (!propertiesFile.isFile() || !propertiesFile.exists()) {
      return null
    }

    val properties = Properties()
    try {
      propertiesFile.inputStream().use {
        properties.load(it)
      }
    }
    catch (_: IOException) {
      return null
    }
    return properties
  }
}