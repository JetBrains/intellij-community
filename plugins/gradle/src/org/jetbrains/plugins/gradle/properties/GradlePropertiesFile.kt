// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.properties

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.getEelDescriptor
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.gradle.properties.GradlePropertiesFile.getPropertyPaths
import org.jetbrains.plugins.gradle.properties.models.getBooleanProperty
import org.jetbrains.plugins.gradle.properties.models.getStringProperty
import org.jetbrains.plugins.gradle.service.execution.gradleUserHomeDir
import org.jetbrains.plugins.gradle.settings.GradleLocalSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.GradleConstants.GRADLE_PROPERTIES_FILE_NAME
import org.jetbrains.plugins.gradle.util.GradleUtil
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists

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
    val propertyPaths = getPropertyPaths(serviceDirectory, resolveGradleProjectRoot(projectPath), null)
    return loadAndMergeProperties(propertyPaths)
  }

  @JvmStatic
  fun getPropertyPaths(project: Project, projectPath: Path): List<Path> {
    return getPropertyPathsInBuildRoot(project, resolveGradleProjectRoot(project, projectPath))
  }

  /**
   * Unlike [getPropertyPaths], expects [buildRoot] to be a Gradle build root already,
   * for example [com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.getExternalRootProjectPath].
   *
   * Therefore, this function never probes the file system to locate the build root,
   * which makes it safe to call under a read action.
   */
  @JvmStatic
  @ApiStatus.Internal
  fun getPropertyPathsInBuildRoot(project: Project, buildRoot: Path): List<Path> {
    val serviceDirectory = GradleSettings.getInstance(project).serviceDirectoryPath
    val gradleHome = GradleLocalSettings.getInstance(project).getGradleHome(buildRoot.toCanonicalPath())
    return getPropertyPaths(serviceDirectory, buildRoot, gradleHome)
  }

  private fun getPropertyPaths(serviceDirectory: String?, buildRoot: Path, gradleHome: String?): List<Path> {
    return listOfNotNull(
      getPropertyPathInGradleUserHome(serviceDirectory, buildRoot.getEelDescriptor()),
      getPropertyPathInBuildRoot(buildRoot),
      getPropertyPathInGradleHome(gradleHome)
    ).map {
      it.toAbsolutePath().normalize()
    }
  }

  fun getPropertyPathInGradleUserHome(serviceDirectory: String?, eelDescriptor: EelDescriptor): Path {
    val gradleUserHome = serviceDirectory?.toNioPathOrNull() ?: gradleUserHomeDir(eelDescriptor)
    return gradleUserHome.resolve(GRADLE_PROPERTIES_FILE_NAME)
  }

  private fun getPropertyPathInBuildRoot(buildRoot: Path): Path {
    return buildRoot.resolve(GRADLE_PROPERTIES_FILE_NAME)
  }

  private fun getPropertyPathInGradleHome(gradleHome: String?): Path? {
    if (gradleHome != null) {
      return Paths.get(gradleHome, GRADLE_PROPERTIES_FILE_NAME)
    }
    return null
  }

  /**
   * Resolves the Gradle build root that owns [projectPath] using the in-memory Gradle settings, and falls back
   * to searching the file system only for paths that aren't known to the IDE.
   *
   * The in-memory resolution performs no IO, which matters because this is called during highlighting and resolution
   * of Gradle scripts under read action.
   */
  @VisibleForTesting
  fun resolveGradleProjectRoot(project: Project, projectPath: Path): Path {
    return resolveGradleProjectRootFromSettings(project, projectPath)
           ?: resolveGradleProjectRoot(projectPath)
  }

  private fun resolveGradleProjectRootFromSettings(project: Project, projectPath: Path): Path? {
    val settings = GradleSettings.getInstance(project)
    val canonicalProjectPath = projectPath.toCanonicalPath()

    // An included build of a composite build is a build root on its own, and it is more specific than the composite root.
    val buildParticipantRoot = settings.linkedProjectsSettings.asSequence()
      .mapNotNull { it.compositeBuild }
      .flatMap { it.compositeParticipants }
      .mapNotNull { it.rootPath }
      .filter { FileUtil.isAncestor(it, canonicalProjectPath, false) }
      .maxByOrNull { it.length }
    if (buildParticipantRoot != null) {
      return Path.of(buildParticipantRoot)
    }

    // Matches a linked project root exactly, or any of its already imported sub-projects.
    val linkedProjectPath = settings.getLinkedProjectSettings(canonicalProjectPath)?.externalProjectPath
    if (linkedProjectPath != null) {
      return Path.of(linkedProjectPath)
    }

    // The project is linked, but not imported yet, so its sub-projects aren't known.
    return settings.linkedProjectsSettings.asSequence()
      .map { it.externalProjectPath }
      .filter { FileUtil.isAncestor(it, canonicalProjectPath, false) }
      .maxByOrNull { it.length }
      ?.let { Path.of(it) }
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