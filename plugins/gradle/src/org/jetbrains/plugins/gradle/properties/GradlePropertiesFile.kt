// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.properties

import com.intellij.openapi.externalSystem.util.environment.Environment
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.properties.GradleProperties.EMPTY
import org.jetbrains.plugins.gradle.properties.base.BasePropertiesFile
import org.jetbrains.plugins.gradle.properties.models.Property
import org.jetbrains.plugins.gradle.settings.GradleLocalSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.nio.file.Path
import java.nio.file.Paths

const val USER_HOME = "user.home"
const val GRADLE_CACHE_DIR_NAME = ".gradle"
const val GRADLE_PROPERTIES_FILE_NAME = "gradle.properties"
const val GRADLE_JAVA_HOME_PROPERTY = "org.gradle.java.home"
const val GRADLE_LOGGING_LEVEL_PROPERTY = "org.gradle.logging.level"
const val GRADLE_PARALLEL_PROPERTY = "org.gradle.parallel"

object GradlePropertiesFile : BasePropertiesFile<GradleProperties>() {

  override val propertiesFileName = GRADLE_PROPERTIES_FILE_NAME

  override fun getProperties(project: Project, externalProjectPath: Path) =
    findAndMergeProperties(getPossiblePropertiesFiles(project, externalProjectPath))

  fun getProperties(serviceDirectoryStr: String?, externalProjectPath: Path) =
    findAndMergeProperties(getPossiblePropertiesFiles(serviceDirectoryStr, externalProjectPath))

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
    return Paths.get(gradleUserHome, propertiesFileName)
  }

  private fun getGradleServiceDirectoryPath(serviceDirectoryStr: String?) =
    serviceDirectoryStr?.let { Paths.get(serviceDirectoryStr, propertiesFileName) }

  fun getGradleHomePropertiesPath(): Path? {
    val gradleUserHome = Environment.getVariable(GradleConstants.SYSTEM_DIRECTORY_PATH_KEY)
    if (gradleUserHome != null) {
      return Paths.get(gradleUserHome, propertiesFileName)
    }

    val userHome = Environment.getProperty(USER_HOME)
    if (userHome != null) {
      return Paths.get(userHome, GRADLE_CACHE_DIR_NAME, propertiesFileName)
    }
    return null
  }

  private fun getGradleProjectPropertiesPath(externalProjectPath: Path) =
    externalProjectPath.resolve(propertiesFileName)

  private fun loadGradleProperties(propertiesPath: Path): GradleProperties {
    val properties = loadProperties(propertiesPath) ?: return EMPTY
    val javaHome = properties.getProperty(GRADLE_JAVA_HOME_PROPERTY)
    val javaHomeProperty = javaHome?.let { Property(it, propertiesPath.toString()) }
    val logLevel = properties.getProperty(GRADLE_LOGGING_LEVEL_PROPERTY)
    val logLevelProperty = logLevel?.let { Property(it, propertiesPath.toString()) }
    val parallel = properties.getProperty(GRADLE_PARALLEL_PROPERTY)?.toBoolean()
    val parallelProperty = parallel?.let { Property(it, propertiesPath.toString()) }
    return GradlePropertiesImpl(javaHomeProperty, logLevelProperty, parallelProperty)
  }

  private fun mergeGradleProperties(most: GradleProperties, other: GradleProperties): GradleProperties {
    return when {
      most is EMPTY -> other
      other is EMPTY -> most
      else -> GradlePropertiesImpl(
        most.javaHomeProperty ?: other.javaHomeProperty,
        most.gradleLoggingLevel ?: other.gradleLoggingLevel,
        most.parallel ?: other.parallel
      )
    }
  }
}