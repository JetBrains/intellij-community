// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.vcs

// @spec ../../../../../../../../spec/layer-locality.spec.md

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.IgnoredBeanFactory
import com.intellij.openapi.vcs.changes.IgnoredFileDescriptor
import com.intellij.openapi.vcs.changes.IgnoredFileProvider
import com.intellij.platform.settings.v2.LayerLocality
import com.intellij.platform.settings.v2.impl.SettingsV2Bundle
import com.intellij.platform.settings.v2.impl.providers.DIRECTORY_CONFIG_FILE_NAME
import com.intellij.platform.settings.v2.impl.providers.JETBRAINS_CONFIG_NAME
import com.intellij.platform.settings.v2.impl.providers.PROJECT_CONFIG_FILE_NAME
import com.intellij.platform.settings.v2.impl.providers.localityFileName
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

/**
 * Declares the local and the environmental config files of the settings engine as ignored for VCS (ADR 0174). For the
 * project layer, these are the files in `<basePath>/.jetbrains/`. For the file layer, these are the files of any
 * directory, so the provider declares them as name masks. The team files stay undeclared. The VCS subsystem decides
 * what to do with the declared files.
 */
internal class SettingsIgnoredFileProvider : IgnoredFileProvider {
  override fun isIgnoredFile(project: Project, filePath: FilePath): Boolean {
    val name = filePath.name
    if (name in ignoredDirectoryConfigNames) return true
    if (name !in ignoredProjectConfigNames) return false
    val projectConfigDir = projectConfigDir(project) ?: return false
    return filePath.parentPath?.path == projectConfigDir.invariantSeparatorsPathString
  }

  override fun getIgnoredFiles(project: Project): Set<IgnoredFileDescriptor> {
    val directoryConfigMasks = ignoredDirectoryConfigNames.map { IgnoredBeanFactory.withMask(it) }
    val projectConfigDir = projectConfigDir(project) ?: return directoryConfigMasks.toSet()
    val projectConfigFiles = ignoredProjectConfigNames.map {
      IgnoredBeanFactory.ignoreFile(projectConfigDir.resolve(it).invariantSeparatorsPathString, project)
    }
    return (directoryConfigMasks + projectConfigFiles).toSet()
  }

  override fun getIgnoredGroupDescription(): @NlsContexts.DetailedDescription String =
    SettingsV2Bundle.message("ignored.files.group.description")

  /**
   * Returns the directory that holds the config files of the project layer, or `null` for a project with no base path.
   */
  private fun projectConfigDir(project: Project): Path? = project.basePath?.let { Path.of(it, JETBRAINS_CONFIG_NAME) }
}

private val ignoredLocalities: List<LayerLocality> = LayerLocality.entries.filter {
  when (it) {
    LayerLocality.TEAM -> false
    LayerLocality.LOCAL, LayerLocality.ENVIRONMENTAL -> true
  }
}

private val ignoredProjectConfigNames: Set<String> =
  ignoredLocalities.mapTo(HashSet()) { localityFileName(PROJECT_CONFIG_FILE_NAME, it) }

private val ignoredDirectoryConfigNames: Set<String> =
  ignoredLocalities.mapTo(HashSet()) { localityFileName(DIRECTORY_CONFIG_FILE_NAME, it) }
