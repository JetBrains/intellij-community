// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.properties.base

import com.intellij.openapi.project.Project
import java.io.IOException
import java.nio.file.Path
import java.util.*
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.isRegularFile

abstract class BasePropertiesFile<PROPERTIES> {

  abstract val propertiesFileName: String

  abstract fun getProperties(project: Project, externalProjectPath: Path): PROPERTIES

  protected fun loadProperties(propertiesFile: Path): Properties? {
    if (!propertiesFile.isRegularFile() || !propertiesFile.exists()) {
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
