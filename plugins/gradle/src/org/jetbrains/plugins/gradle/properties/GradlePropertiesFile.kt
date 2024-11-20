// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.properties

import com.intellij.openapi.externalSystem.util.environment.Environment
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.properties.GradleProperties.EMPTY
import org.jetbrains.plugins.gradle.properties.models.getBooleanProperty
import org.jetbrains.plugins.gradle.properties.models.getStringProperty
import org.jetbrains.plugins.gradle.settings.GradleLocalSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.GradleUtil
import java.nio.file.Path
import java.nio.file.Paths

const val USER_HOME = "user.home"
const val GRADLE_CACHE_DIR_NAME = ".gradle"
const val GRADLE_PROPERTIES_FILE_NAME = "gradle.properties"
const val GRADLE_JAVA_HOME_PROPERTY = "org.gradle.java.home"
const val GRADLE_LOGGING_LEVEL_PROPERTY = "org.gradle.logging.level"
const val GRADLE_PARALLEL_PROPERTY = "org.gradle.parallel"
const val GRADLE_JVM_OPTIONS_PROPERTY = "org.gradle.jvmargs"
const val GRADLE_ISOLATED_PROJECTS_PROPERTY = "org.gradle.unsafe.isolated-projects"

object GradlePropertiesFile {

  @JvmStatic
  fun getProperties(project: Project, projectPath: Path) =
    findAndMergeProperties(getPossiblePropertiesFiles(project, projectPath))

  @JvmStatic
  fun getProperties(serviceDirectoryStr: String?, projectPath: Path) =
    findAndMergeProperties(getPossiblePropertiesFiles(serviceDirectoryStr, projectPath))

  private fun findAndMergeProperties(possiblePropertiesFiles: List<Path>): GradleProperties {
    return possiblePropertiesFiles
      .asSequence()
      .map { it.toAbsolutePath().normalize() }
      .map(::loadGradleProperties)
      .reduce(::mergeGradleProperties)
  }

  private fun getPossiblePropertiesFiles(project: Project, projectPath: Path): List<Path> {
    return listOfNotNull(
      getGradleServiceDirectoryPath(project),
      getGradleHomePropertiesPath(),
      getGradleProjectPropertiesPath(projectPath)
    )
  }

  private fun getPossiblePropertiesFiles(serviceDirectoryStr: String?, projectPath: Path): List<Path> {
    return listOfNotNull(
      getGradleServiceDirectoryPath(serviceDirectoryStr),
      getGradleHomePropertiesPath(),
      getGradleProjectPropertiesPath(projectPath)
    )
  }

  private fun getGradleServiceDirectoryPath(project: Project): Path? {
    val gradleUserHome = GradleLocalSettings.getInstance(project).gradleUserHome ?: return null
    return Paths.get(gradleUserHome, GRADLE_PROPERTIES_FILE_NAME)
  }

  private fun getGradleServiceDirectoryPath(serviceDirectoryStr: String?) =
    serviceDirectoryStr?.let { Paths.get(serviceDirectoryStr, GRADLE_PROPERTIES_FILE_NAME) }

  fun getGradleHomePropertiesPath(): Path? {
    val gradleUserHome = Environment.getVariable(GradleConstants.SYSTEM_DIRECTORY_PATH_KEY)
    if (gradleUserHome != null) {
      return Paths.get(gradleUserHome, GRADLE_PROPERTIES_FILE_NAME)
    }

    val userHome = Environment.getProperty(USER_HOME)
    if (userHome != null) {
      return Paths.get(userHome, GRADLE_CACHE_DIR_NAME, GRADLE_PROPERTIES_FILE_NAME)
    }
    return null
  }

  private fun getGradleProjectPropertiesPath(projectPath: Path) =
    projectPath.resolve(GRADLE_PROPERTIES_FILE_NAME)

  private fun loadGradleProperties(propertiesPath: Path): GradleProperties {
    val properties = GradleUtil.readGradleProperties(propertiesPath) ?: return EMPTY
    return GradlePropertiesImpl(
      javaHomeProperty = properties.getStringProperty(GRADLE_JAVA_HOME_PROPERTY, propertiesPath),
      gradleLoggingLevel = properties.getStringProperty(GRADLE_LOGGING_LEVEL_PROPERTY, propertiesPath),
      parallel = properties.getBooleanProperty(GRADLE_PARALLEL_PROPERTY, propertiesPath),
      isolatedProjects = properties.getBooleanProperty(GRADLE_ISOLATED_PROJECTS_PROPERTY, propertiesPath),
      jvmOptions = properties.getStringProperty(GRADLE_JVM_OPTIONS_PROPERTY, propertiesPath)
    )
  }

  private fun mergeGradleProperties(most: GradleProperties, other: GradleProperties): GradleProperties {
    return when {
      most is EMPTY -> other
      other is EMPTY -> most
      else -> GradlePropertiesImpl(
        most.javaHomeProperty ?: other.javaHomeProperty,
        most.gradleLoggingLevel ?: other.gradleLoggingLevel,
        most.parallel ?: other.parallel,
        most.isolatedProjects ?: other.isolatedProjects,
        most.jvmOptions ?: other.jvmOptions
      )
    }
  }
}