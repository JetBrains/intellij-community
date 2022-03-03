// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("GradlePropertiesUtil")
package org.jetbrains.plugins.gradle.util

import com.intellij.openapi.externalSystem.util.environment.Environment
import com.intellij.openapi.project.Project
import com.intellij.util.io.exists
import com.intellij.util.io.inputStream
import com.intellij.util.io.isFile
import org.jetbrains.plugins.gradle.settings.GradleLocalSettings
import org.jetbrains.plugins.gradle.util.GradleProperties.EMPTY
import org.jetbrains.plugins.gradle.util.GradleProperties.GradleProperty
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

const val USER_HOME = "user.home"
const val GRADLE_CACHE_DIR_NAME = ".gradle"
const val PROPERTIES_FILE_NAME = "gradle.properties"
const val GRADLE_JAVA_HOME_PROPERTY = "org.gradle.java.home"
const val GRADLE_LOGGING_LEVEL_PROPERTY = "org.gradle.logging.level"

fun getGradleProperties(project: Project, externalProjectPath: Path): GradleProperties {
  return findAndMergeProperties(getPossiblePropertiesFiles(project, externalProjectPath))
}

fun getGradleProperties(serviceDirectoryStr: String?, externalProjectPath: Path): GradleProperties {
  return findAndMergeProperties(getPossiblePropertiesFiles(serviceDirectoryStr, externalProjectPath))
}

private fun findAndMergeProperties(possiblePropertiesFiles: List<Path>): GradleProperties {
  return possiblePropertiesFiles
    .asSequence()
    .map { it.toAbsolutePath().normalize() }
    .map(::loadGradleProperties)
    .reduce(::mergeGradleProperties)
}

private fun getPossiblePropertiesFiles(project: Project, externalProjectPath: Path): List<Path> {
  return listOfNotNull(
    getGradleServiceDirectoryPath(project),
    getGradleHomePropertiesPath(),
    getGradleProjectPropertiesPath(externalProjectPath)
  )
}

private fun getPossiblePropertiesFiles(serviceDirectoryStr: String?, externalProjectPath: Path): List<Path> {
  return listOfNotNull(
    getGradleServiceDirectoryPath(serviceDirectoryStr),
    getGradleHomePropertiesPath(),
    getGradleProjectPropertiesPath(externalProjectPath)
  )
}

private fun getGradleServiceDirectoryPath(project: Project): Path? {
  val gradleUserHome = GradleLocalSettings.getInstance(project).gradleUserHome ?: return null
  return Paths.get(gradleUserHome, PROPERTIES_FILE_NAME)
}


private fun getGradleServiceDirectoryPath(serviceDirectoryStr: String?): Path? {
  return serviceDirectoryStr?.let { Paths.get(serviceDirectoryStr, PROPERTIES_FILE_NAME) }
}

private fun getGradleHomePropertiesPath(): Path? {
  val gradleUserHome = Environment.getVariable(GradleConstants.SYSTEM_DIRECTORY_PATH_KEY)
  if (gradleUserHome != null) {
    return Paths.get(gradleUserHome, PROPERTIES_FILE_NAME)
  }

  val userHome = Environment.getProperty(USER_HOME)
  if (userHome != null) {
    return Paths.get(userHome, GRADLE_CACHE_DIR_NAME, PROPERTIES_FILE_NAME)
  }
  return null
}

private fun getGradleProjectPropertiesPath(externalProjectPath: Path): Path {
  return externalProjectPath.resolve(PROPERTIES_FILE_NAME)
}

private fun loadGradleProperties(propertiesPath: Path): GradleProperties {
  val properties = loadProperties(propertiesPath) ?: return EMPTY
  val javaHome = properties.getProperty(GRADLE_JAVA_HOME_PROPERTY)
  val javaHomeProperty = javaHome?.let { GradleProperty(it, propertiesPath.toString()) }
  val logLevel = properties.getProperty(GRADLE_LOGGING_LEVEL_PROPERTY)
  val logLevelProperty = logLevel?.let { GradleProperty(it, propertiesPath.toString()) }
  return GradlePropertiesImpl(javaHomeProperty, logLevelProperty)
}

private fun loadProperties(propertiesFile: Path): Properties? {
  if (!propertiesFile.isFile() || !propertiesFile.exists()) {
    return null
  }

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
    else -> GradlePropertiesImpl(most.javaHomeProperty ?: other.javaHomeProperty,
    most.gradleLoggingLevel ?: other.gradleLoggingLevel)
  }
}

private data class GradlePropertiesImpl(override val javaHomeProperty: GradleProperty<String>?,
                                        override val gradleLoggingLevel: GradleProperty<String>?) : GradleProperties