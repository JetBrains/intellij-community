// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("GradlePropertiesUtil")

package org.jetbrains.plugins.gradle.util

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.plugins.gradle.util.GradleProperties.EMPTY
import org.jetbrains.plugins.gradle.util.GradleProperties.GradleProperty
import java.io.File
import java.util.*

const val GRADLE_CACHE_DIR_NAME = ".gradle"
const val PROPERTIES_FILE_NAME = "gradle.properties"
const val GRADLE_JAVA_HOME_PROPERTY = "org.gradle.java.home"

fun getGradleProperties(externalProjectPath: String): GradleProperties {
  return getPossiblePropertiesFiles(externalProjectPath)
    .asSequence()
    .map(FileUtil::toCanonicalPath)
    .map(::loadGradleProperties)
    .reduce(::mergeGradleProperties)
}

private fun getPossiblePropertiesFiles(externalProjectPath: String): List<String> {
  return listOfNotNull(
    getGradleHomePropertiesPath(),
    getGradleProjectPropertiesPath(externalProjectPath)
  )
}

private fun getGradleHomePropertiesPath(): String {
  val gradleUserHome = Environment.getEnvVariable(GradleConstants.SYSTEM_DIRECTORY_PATH_KEY)
  if (gradleUserHome != null) {
    return FileUtil.join(gradleUserHome, PROPERTIES_FILE_NAME)
  }
  val userHome = Environment.getUserHome()
  return FileUtil.join(userHome, GRADLE_CACHE_DIR_NAME, PROPERTIES_FILE_NAME)
}

private fun getGradleProjectPropertiesPath(externalProjectPath: String): String {
  return FileUtil.join(externalProjectPath, PROPERTIES_FILE_NAME)
}

private fun loadGradleProperties(propertiesPath: String): GradleProperties {
  val properties = loadProperties(propertiesPath) ?: return EMPTY
  val javaHome = properties.getProperty(GRADLE_JAVA_HOME_PROPERTY)
  val javaHomeProperty = javaHome?.let { GradleProperty(it, propertiesPath) }
  return GradlePropertiesImpl(javaHomeProperty)
}

private fun loadProperties(propertiesPath: String): Properties? {
  val propertiesFile = File(propertiesPath)
  if (!propertiesFile.isFile) return null
  if (!propertiesFile.exists()) return null
  val properties = Properties()
  propertiesFile.inputStream().use {
    properties.load(it)
  }
  return properties
}

private fun mergeGradleProperties(most: GradleProperties, other: GradleProperties): GradleProperties {
  return when {
    most is EMPTY -> other
    other is EMPTY -> most
    else -> GradlePropertiesImpl(most.javaHomeProperty ?: other.javaHomeProperty)
  }
}

private data class GradlePropertiesImpl(override val javaHomeProperty: GradleProperty<String>?) : GradleProperties