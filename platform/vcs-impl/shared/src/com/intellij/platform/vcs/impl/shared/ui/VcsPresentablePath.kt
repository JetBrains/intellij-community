// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.ui

import com.intellij.idea.AppMode
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.platform.vcs.impl.shared.CaseSensitivityInfoHolder
import com.intellij.platform.vcs.impl.shared.ProjectBasePathHolder
import com.intellij.platform.vcs.impl.shared.VcsMappingsHolder
import org.jetbrains.annotations.SystemDependent
import org.jetbrains.annotations.SystemIndependent
import org.jetbrains.annotations.VisibleForTesting

/**
 * Implementation is identical to [com.intellij.vcsUtil.VcsUtil.getPresentablePath] with support for remote development:
 * 1. Don't relativize home paths
 * 2. Use a system-independent path format
 */
internal object VcsPresentablePath {
  @JvmStatic
  fun getPresentablePath(parentPath: FilePath, path: FilePath): @NlsSafe @SystemDependent String {
    val prettyPath = relativizeSystemIndependentPaths(parentPath.path, path.path) ?: path.path
    return if (shouldHandleAsNonLocal(path)) prettyPath else getSystemDependentPath(prettyPath)
  }

  @JvmStatic
  fun getPresentablePath(project: Project?, filePath: FilePath, forceRelativePath: Boolean = false): @NlsSafe @SystemDependent String =
    if (!forceRelativePath && shouldHandleAsNonLocal(filePath)) filePath.path
    else getPresentablePathOrEmpty(project, filePath, canBeEmpty = !forceRelativePath)

  private fun getPresentablePathOrEmpty(project: Project?, filePath: FilePath, canBeEmpty: Boolean): @NlsSafe @SystemDependent String {
    if (project == null || !project.isDisposed) {
      val projectDir = project?.service<ProjectBasePathHolder>()?.getPresentablePath()
      if (projectDir != null) {
        val relativePath =
          getRelativePathToSingleVcsRootOrProjectDir(VcsMappingsHolder.getInstance(project), projectDir, filePath, canBeEmpty)
        if (relativePath != null) return getSystemDependentPath(relativePath)
      }
    }

    return getRelativePathToUserHome(filePath)
  }

  @VisibleForTesting
  internal fun getRelativePathToSingleVcsRootOrProjectDir(
    vcsMappingsHolder: VcsMappingsHolder,
    projectBaseDir: FilePath,
    filePath: FilePath,
    acceptEmptyPath: Boolean,
  ): @SystemIndependent String? {
    val vcsRootForFile = vcsMappingsHolder.getRootFor(filePath)
    return when {
      vcsRootForFile == null -> {
        val relativePathToProjectDir = getRelativePathIfSuccessor(projectBaseDir.path, filePath.path) ?: return null
        VcsBundle.message("label.relative.project.path.presentation", relativePathToProjectDir)
      }
      vcsMappingsHolder.getAllRoots().size == 1 -> getRelativePathToSingleRoot(vcsRootForFile, filePath, acceptEmptyPath)
      // Multiple roots scenarios
      projectBaseDir == filePath -> vcsRootForFile.name
      else -> {
        val relativePathToProjectDir = getRelativePathIfSuccessor(projectBaseDir.path, filePath.path) ?: return null
        if (projectBaseDir == vcsRootForFile) "${projectBaseDir.name}/$relativePathToProjectDir" else relativePathToProjectDir
      }
    }
  }

  /**
   * @return the relative path to [filePath] from [vcsRootForFile] in case if there is only one VCS root registered.
   */
  private fun getRelativePathToSingleRoot(
    vcsRootForFile: FilePath,
    filePath: FilePath,
    acceptEmptyPath: Boolean,
  ): @SystemIndependent String? = when {
    vcsRootForFile != filePath -> getRelativePathIfSuccessor(vcsRootForFile.path, filePath.path)
    acceptEmptyPath -> ""
    else -> vcsRootForFile.name
  }

  private fun relativizeSystemIndependentPaths(
    ancestor: @SystemIndependent String,
    path: @SystemIndependent String,
  ): @NlsSafe @SystemIndependent String? =
    FileUtil.getRelativePath(ancestor, path, '/', CaseSensitivityInfoHolder.caseSensitive)

  private fun getRelativePathIfSuccessor(
    ancestor: @SystemIndependent String,
    path: @SystemIndependent String,
  ): @NlsSafe @SystemIndependent String? =
    if (FileUtil.isAncestor(ancestor, path, true)) relativizeSystemIndependentPaths(ancestor, path) else null

  /**
   * In split mode we operate [com.intellij.openapi.vcs.RemoteFilePath], so it's always `filePath.isNonLocal == true`.
   * However, it still makes sense to try to show relative paths in the UI.
   */
  private fun shouldHandleAsNonLocal(filePath: FilePath): Boolean = AppMode.isMonolith() && filePath.isNonLocal

  private fun getRelativePathToUserHome(filePath: FilePath): @NlsSafe @SystemDependent String =
    if (AppMode.isMonolith()) FileUtil.getLocationRelativeToUserHome(getSystemDependentPath(filePath.path)) else filePath.path

  private fun getSystemDependentPath(path: String): @NlsSafe @SystemDependent String =
    if (AppMode.isMonolith()) FileUtil.toSystemDependentName(path) else path
}
