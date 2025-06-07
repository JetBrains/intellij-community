// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.properties

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.toCanonicalPath
import org.jetbrains.plugins.gradle.properties.models.getBooleanProperty
import org.jetbrains.plugins.gradle.properties.models.getStringProperty
import org.jetbrains.plugins.gradle.service.execution.gradleUserHomeDir
import org.jetbrains.plugins.gradle.settings.GradleLocalSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.GradleConstants.GRADLE_CACHE_DIR_NAME
import org.jetbrains.plugins.gradle.util.GradleConstants.GRADLE_PROPERTIES_FILE_NAME
import org.jetbrains.plugins.gradle.util.GradleConstants.USER_HOME_PROPERTY_KEY
import org.jetbrains.plugins.gradle.util.GradleUtil
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists

@Deprecated("Use GradleConstants#USER_HOME_PROPERTY_KEY instead")
const val USER_HOME: String = USER_HOME_PROPERTY_KEY

@Deprecated("Use GradleConstants#GRADLE_CACHE_DIR_NAME instead")
const val GRADLE_CACHE_DIR_NAME: String = GRADLE_CACHE_DIR_NAME

@Deprecated("Use GradleConstants#GRADLE_PROPERTIES_FILE_NAME instead")
const val GRADLE_PROPERTIES_FILE_NAME: String = GRADLE_PROPERTIES_FILE_NAME

const val GRADLE_JAVA_HOME_PROPERTY: String = "org.gradle.java.home"
const val GRADLE_LOGGING_LEVEL_PROPERTY: String = "org.gradle.logging.level"
const val GRADLE_PARALLEL_PROPERTY: String = "org.gradle.parallel"
const val GRADLE_JVM_OPTIONS_PROPERTY: String = "org.gradle.jvmargs"
const val GRADLE_ISOLATED_PROJECTS_PROPERTY: String = "org.gradle.unsafe.isolated-projects"

object GradlePropertiesFile {

  @JvmStatic
  fun getProperties(project: Project, projectPath: Path): GradleProperties {
    val propertyPaths = getPropertyPaths(project, projectPath)
    return loadAndMergeProperties(propertyPaths)
  }

  @JvmStatic
  fun getProperties(serviceDirectory: String?, projectPath: Path): GradleProperties {
    val propertyPaths = getPropertyPaths(serviceDirectory, projectPath, null)
    return loadAndMergeProperties(propertyPaths)
  }

  @JvmStatic
  fun getPropertyPaths(project: Project, projectPath: Path): List<Path> {
    val linkedProjectPath = resolveGradleProjectRoot(projectPath).toCanonicalPath()
    val serviceDirectory = GradleSettings.getInstance(project).serviceDirectoryPath
    val gradleHome = GradleLocalSettings.getInstance(project).getGradleHome(linkedProjectPath)
    return getPropertyPaths(serviceDirectory, projectPath, gradleHome)
  }

  private fun getPropertyPaths(serviceDirectory: String?, projectPath: Path, gradleHome: String?): List<Path> {
    return listOfNotNull(
      getPropertyPathInGradleUserHome(serviceDirectory),
      getPropertyPathInGradleProjectRoot(projectPath),
      getPropertyPathInGradleHome(gradleHome)
    ).map {
      it.toAbsolutePath().normalize()
    }
  }

  fun getPropertyPathInGradleUserHome(serviceDirectory: String?): Path? {
    val gradleUserHome = serviceDirectory ?: gradleUserHomeDir().path
    if (gradleUserHome != null) {
      return Paths.get(gradleUserHome, GRADLE_PROPERTIES_FILE_NAME)
    }
    return null
  }

  private fun getPropertyPathInGradleProjectRoot(projectPath: Path): Path {
    return resolveGradleProjectRoot(projectPath)
      .resolve(GRADLE_PROPERTIES_FILE_NAME)
  }

  private fun getPropertyPathInGradleHome(gradleHome: String?): Path? {
    if (gradleHome != null) {
      return Paths.get(gradleHome, GRADLE_PROPERTIES_FILE_NAME)
    }
    return null
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

  private fun loadAndMergeProperties(propertyPaths: List<Path>): GradleProperties {
    return propertyPaths.asSequence()
      .mapNotNull(::loadGradleProperties)
      .fold(EMPTY, ::mergeGradleProperties)
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

  private val EMPTY = GradlePropertiesImpl(
    javaHomeProperty = null,
    logLevel = null,
    parallel = null,
    isolatedProjects = null,
    jvmOptions = null,
  )
}