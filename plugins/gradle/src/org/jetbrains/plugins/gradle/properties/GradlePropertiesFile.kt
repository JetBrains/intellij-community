// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.properties

import com.intellij.openapi.externalSystem.util.environment.Environment
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.properties.models.getBooleanProperty
import org.jetbrains.plugins.gradle.properties.models.getStringProperty
import org.jetbrains.plugins.gradle.settings.GradleLocalSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.GradleUtil
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists

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
  fun getProperties(project: Project, projectPath: Path): GradleProperties {
    val serviceDirectory = GradleLocalSettings.getInstance(project).gradleUserHome
    return getProperties(serviceDirectory, projectPath)
  }

  @JvmStatic
  fun getProperties(serviceDirectory: String?, projectPath: Path): GradleProperties {
    return loadAndMergeProperties(
      getGradlePropertiesPathInServiceDirectory(serviceDirectory),
      getGradlePropertiesPathInUserHome(),
      getGradlePropertiesPathInProject(projectPath)
    )
  }

  private fun loadAndMergeProperties(vararg possiblePropertiesFiles: Path?): GradleProperties {
    return possiblePropertiesFiles.asSequence()
      .filterNotNull()
      .map(Path::toAbsolutePath)
      .map(Path::normalize)
      .map(::loadGradleProperties)
      .filterNotNull()
      .fold(EMPTY_GRADLE_PROPERTIES, ::mergeGradleProperties)
  }

  private fun getGradlePropertiesPathInServiceDirectory(serviceDirectory: String?): Path? {
    if (serviceDirectory != null) {
      return Paths.get(serviceDirectory, GRADLE_PROPERTIES_FILE_NAME)
    }
    return null
  }

  fun getGradlePropertiesPathInUserHome(): Path? {
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

  private fun getGradlePropertiesPathInProject(projectPath: Path): Path {
    return resolveGradleProjectRoot(projectPath)
      .resolve(GRADLE_PROPERTIES_FILE_NAME)
  }

  private fun resolveGradleProjectRoot(projectPath: Path): Path {
    var buildRoot: Path? = projectPath
    while (buildRoot != null) {
      for (settingsFileName in GradleConstants.KNOWN_GRADLE_SETTINGS_FILES) {
        val settingsFile = buildRoot.resolve(settingsFileName)
        if (settingsFile.exists()) {
          return buildRoot
        }
      }
      buildRoot = buildRoot.parent
    }
    return projectPath
  }

  private fun loadGradleProperties(propertiesPath: Path): GradleProperties? {
    val properties = GradleUtil.readGradleProperties(propertiesPath) ?: return null
    return GradlePropertiesImpl(
      javaHomeProperty = properties.getStringProperty(GRADLE_JAVA_HOME_PROPERTY, propertiesPath),
      logLevel = properties.getStringProperty(GRADLE_LOGGING_LEVEL_PROPERTY, propertiesPath),
      parallel = properties.getBooleanProperty(GRADLE_PARALLEL_PROPERTY, propertiesPath),
      isolatedProjects = properties.getBooleanProperty(GRADLE_ISOLATED_PROJECTS_PROPERTY, propertiesPath),
      jvmOptions = properties.getStringProperty(GRADLE_JVM_OPTIONS_PROPERTY, propertiesPath)
    )
  }

  private fun mergeGradleProperties(most: GradleProperties, other: GradleProperties): GradleProperties {
    return GradlePropertiesImpl(
      javaHomeProperty = most.javaHomeProperty ?: other.javaHomeProperty,
      logLevel = most.logLevel ?: other.logLevel,
      parallel = most.parallel ?: other.parallel,
      isolatedProjects = most.isolatedProjects ?: other.isolatedProjects,
      jvmOptions = most.jvmOptions ?: other.jvmOptions
    )
  }

  private val EMPTY_GRADLE_PROPERTIES = GradlePropertiesImpl(
    javaHomeProperty = null,
    logLevel = null,
    parallel = null,
    isolatedProjects = null,
    jvmOptions = null,
  )
}