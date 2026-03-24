// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.text.StringUtil
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.settings.DistributionType
import org.jetbrains.plugins.gradle.settings.GradleLocalSettings
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Objects
import java.util.Properties
import java.util.regex.Pattern
import kotlin.io.path.isDirectory

/**
 * This file should be removed as soon as possible. It was introduced only as a fallback to provide compatibility with external plugins
 * during the deprecation lifecycle of [org.jetbrains.plugins.gradle.settings.GradleProjectSettings.resolveGradleVersion].
 */
@Deprecated("Use [GradleInstallationManager.guessGradleVersion] instead")
internal object GradleProjectSettingsHelper {

  private val GRADLE_JAR_FILE_PATTERN: Pattern =
    Pattern.compile(System.getProperty("gradle.pattern.core.jar", "gradle-(core-)?(\\d.*)\\.jar"))

  @JvmStatic
  fun guessGradleVersion(settings: GradleProjectSettings): GradleVersion = try {
    guessGradleVersionInternal(settings) ?: GradleVersion.current()
  }
  catch (_: Exception) {
    return GradleVersion.current()
  }

  private fun guessGradleVersionInternal(settings: GradleProjectSettings): GradleVersion? {
    val distributionType = settings.distributionType ?: return null
    return when (distributionType) {
      DistributionType.BUNDLED -> GradleVersion.current()
      DistributionType.LOCAL -> {
        val gradleHome = findGradleHome(settings) ?: return null
        getGradleVersion(gradleHome)
      }
      DistributionType.DEFAULT_WRAPPED, DistributionType.WRAPPED -> {
        val gradleHome = findGradleHome(settings)
        if (gradleHome != null) {
          val gradleVersion = getGradleVersion(gradleHome)
          if (gradleVersion != null) {
            return gradleVersion
          }
        }
        val externalProjectPath = settings.externalProjectPath ?: return null
        val path = findDefaultWrapperPropertiesFile(Path.of(externalProjectPath))
        if (path != null) {
          val properties = readGradleProperties(path) ?: return null
          val distribution = properties["distributionUrl"] ?: return null
          return parseDistributionVersion(distribution.toString())
        }
        null
      }
    }
  }

  private fun findGradleHome(settings: GradleProjectSettings): Path? {
    if (settings.gradleHomePath != null) {
      return settings.gradleHomePath
    }
    val projectPath = settings.externalProjectPath ?: return null
    val project = findProject(settings)
    val localSettings = GradleLocalSettings.getInstance(project)
    val gradleHome = localSettings.getGradleHome(projectPath) ?: return null
    return Path.of(gradleHome)
  }

  private fun findDefaultWrapperPropertiesFile(root: Path?): Path? {
    if (root == null) {
      return null
    }
    // There is a possible case that given path points to a gradle script (*.gradle) but it's also possible that
    // it references script's directory. We want to provide flexibility here.
    val gradleDir = if (Files.isRegularFile(root)) root.resolveSibling("gradle") else root.resolve("gradle")
    if (!Files.isDirectory(gradleDir)) {
      return null
    }

    val wrapperDir = gradleDir.resolve("wrapper")
    if (!Files.isDirectory(wrapperDir)) {
      return null
    }

    try {
      Files.list(wrapperDir)
        .use { pathsStream ->
          val candidates = pathsStream
            .filter { FileUtilRt.extensionEquals(it.fileName.toString(), "properties") && Files.isRegularFile(it) }
            .toList()
          if (candidates.size != 1) {
            return null
          }
          return candidates[0]
        }
    }
    catch (_: IOException) {
      return null
    }
  }

  private fun readGradleProperties(propertiesFile: Path): Properties? {
    return try {
      Files.newBufferedReader(propertiesFile, StandardCharsets.ISO_8859_1)
        .use { reader ->
          val props = Properties()
          props.load(reader)
          props
        }
    }
    catch (_: Exception) {
      null
    }
  }

  private fun parseDistributionVersion(string: String): GradleVersion? {
    var path = string
    path = StringUtil.substringAfterLast(path, "/") ?: return null
    path = StringUtil.substringAfterLast(path, "gradle-") ?: return null
    val i = path.lastIndexOf('-')
    if (i <= 0) {
      return null
    }
    return getGradleVersionSafe(path.substring(0, i))
  }

  private fun getGradleVersionSafe(gradleVersion: String): GradleVersion? {
    try {
      return GradleVersion.version(gradleVersion)
    }
    catch (_: IllegalArgumentException) {
      return null
    }
  }

  private fun getGradleVersion(gradleHome: Path): GradleVersion? {
    val libs = gradleHome.resolve("lib")
    if (!libs.isDirectory()) {
      return null
    }
    try {
      Files.list(libs).use { children ->
        val gradleVersion = children.map<String?> { path: Path? ->
          val fileName = path!!.fileName
          if (fileName != null) {
            val matcher = GRADLE_JAR_FILE_PATTERN.matcher(fileName.toString())
            if (matcher.matches()) {
              return@map matcher.group(2)
            }
          }
          null
        }.filter { Objects.nonNull(it) }.findFirst().orElse(null)
        return GradleVersion.version(gradleVersion)
      }
    }
    catch (_: IOException) {
      return null
    }
  }

  private fun findProject(settings: GradleProjectSettings): Project {
    for (project in ProjectManager.getInstance().getOpenProjects()) {
      val linkedProjectSettings = GradleSettings.getInstance(project).getLinkedProjectSettings(settings.externalProjectPath)
      if (linkedProjectSettings === settings) {
        return project
      }
    }
    return ProjectManager.getInstance().getDefaultProject()
  }
}