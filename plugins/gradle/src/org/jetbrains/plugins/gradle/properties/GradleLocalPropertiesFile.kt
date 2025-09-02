// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.properties

import org.jetbrains.plugins.gradle.properties.models.getStringProperty
import org.jetbrains.plugins.gradle.util.GradleConstants.GRADLE_CACHE_DIR_NAME
import org.jetbrains.plugins.gradle.util.GradleConstants.GRADLE_LOCAL_PROPERTIES_FILE_NAME
import org.jetbrains.plugins.gradle.util.GradleUtil
import java.nio.file.Path
import java.nio.file.Paths

const val GRADLE_LOCAL_JAVA_HOME_PROPERTY: String = "java.home"

@Deprecated("Use GradleConstants#GRADLE_LOCAL_PROPERTIES_FILE_NAME instead")
const val GRADLE_LOCAL_PROPERTIES_FILE_NAME: String = GRADLE_LOCAL_PROPERTIES_FILE_NAME

object GradleLocalPropertiesFile {

  @JvmStatic
  fun getProperties(externalProjectPath: Path): GradleLocalProperties {
    val propertiesPath = getPropertyPath(externalProjectPath)
    return loadGradleLocalProperties(propertiesPath) ?: EMPTY
  }

  @JvmStatic
  fun getPropertyPath(externalProjectPath: Path): Path {
    return externalProjectPath.resolve(Paths.get(GRADLE_CACHE_DIR_NAME, GRADLE_LOCAL_PROPERTIES_FILE_NAME))
      .toAbsolutePath().normalize()
  }

  private fun loadGradleLocalProperties(propertiesPath: Path): GradleLocalProperties? {
    val properties = GradleUtil.readGradleProperties(propertiesPath) ?: return null
    return GradleLocalPropertiesImpl(
      javaHomeProperty = properties.getStringProperty(GRADLE_LOCAL_JAVA_HOME_PROPERTY, propertiesPath)
    )
  }

  private val EMPTY = GradleLocalPropertiesImpl(
    javaHomeProperty = null
  )
}