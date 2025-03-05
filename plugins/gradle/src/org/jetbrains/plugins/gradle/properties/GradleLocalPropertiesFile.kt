// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.properties

import org.jetbrains.plugins.gradle.properties.models.getStringProperty
import org.jetbrains.plugins.gradle.util.GradleUtil
import java.nio.file.Path
import java.nio.file.Paths

const val GRADLE_LOCAL_JAVA_HOME_PROPERTY = "java.home"
const val GRADLE_LOCAL_PROPERTIES_FILE_NAME = "config.properties"

object GradleLocalPropertiesFile {

  fun getProperties(externalProjectPath: Path): GradleLocalProperties {
    val propertiesPath = getGradleLocalPropertiesPath(externalProjectPath)
    return loadGradleLocalProperties(propertiesPath)
  }

  private fun getGradleLocalPropertiesPath(externalProjectPath: Path): Path {
    val gradleLocalPath = externalProjectPath.resolve(Paths.get(GRADLE_CACHE_DIR_NAME, GRADLE_LOCAL_PROPERTIES_FILE_NAME))
    return gradleLocalPath.toAbsolutePath().normalize()
  }

  private fun loadGradleLocalProperties(propertiesPath: Path): GradleLocalProperties {
    val properties = GradleUtil.readGradleProperties(propertiesPath) ?: return EMPTY_GRADLE_PROPERTIES
    return GradleLocalPropertiesImpl(
      javaHomeProperty = properties.getStringProperty(GRADLE_LOCAL_JAVA_HOME_PROPERTY, propertiesPath)
    )
  }

  private val EMPTY_GRADLE_PROPERTIES = GradleLocalPropertiesImpl(
    javaHomeProperty = null
  )
}