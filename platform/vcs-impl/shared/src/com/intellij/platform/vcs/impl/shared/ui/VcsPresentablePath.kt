// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.ui

import com.intellij.idea.AppModeAssertions
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

/**
 * Implementation is identical to [com.intellij.vcsUtil.VcsUtil.getPresentablePath] with support for remote development:
 * 1. Don't relativize home paths
 * 2. Use a system-independent path format
 */
internal object VcsPresentablePath {
  @JvmStatic
  fun getPresentablePath(parentPath: FilePath, path: FilePath): @NlsSafe @SystemDependent String {
    val prettyPath = getRelativePathIfSuccessor(parentPath.path, path.path) ?: path.path
    return if (shouldHandleAsNonLocal(path)) prettyPath else getSystemDependentPath(prettyPath)
  }

  @JvmStatic
  fun getPresentablePath(project: Project?, filePath: FilePath): @NlsSafe @SystemDependent String =
    if (shouldHandleAsNonLocal(filePath)) filePath.path else getPresentablePath(project, filePath, acceptEmptyPath = false)

  @JvmStatic
  fun getPresentablePathAsParent(project: Project?, filePath: FilePath): @NlsSafe @SystemDependent String =
    getPresentablePath(project, filePath, acceptEmptyPath = true)

  private fun getPresentablePath(project: Project?, filePath: FilePath, acceptEmptyPath: Boolean): @NlsSafe @SystemDependent String {
    val projectDir = project?.service<ProjectBasePathHolder>()?.getPresentablePath()
    if (projectDir != null) {
      val rootRelativePath = getRootRelativePath(project, projectDir, filePath, acceptEmptyPath)
      if (rootRelativePath != null) return getSystemDependentPath(rootRelativePath)

      val projectRelativePath = getRelativePathIfSuccessor(projectDir, filePath.path)
      if (projectRelativePath != null) return getSystemDependentPath(VcsBundle.message("label.relative.project.path.presentation", projectRelativePath))
    }

    return getRelativePathToUserHome(filePath)
  }

  private fun getRootRelativePath(project: Project,
    projectBaseDir: String,
    filePath: FilePath,
    acceptEmptyPath: Boolean,
  ): @SystemIndependent String? {
    if (project.isDisposed()) return null
    val path = filePath.path

    val vcsMappingsHolder = VcsMappingsHolder.getInstance(project)
    val root = vcsMappingsHolder.getRootFor(filePath) ?: return null

    val rootPath = root.path

    val roots = vcsMappingsHolder.getAllRoots()
    if (roots.size == 1) {
      if (rootPath == path) return if (acceptEmptyPath) "" else root.getName()

      return getRelativePathIfSuccessor(rootPath, path)
    }

    if (projectBaseDir == path) return root.getName()

    val relativePath = getRelativePathIfSuccessor(projectBaseDir, path) ?: return null

    return if (projectBaseDir == rootPath) root.getName() + '/' + relativePath else relativePath
  }

  private fun getRelativePathIfSuccessor(ancestor: String, path: String): @NlsSafe @SystemIndependent String? =
    FileUtil.getRelativePath(ancestor, path, '/', CaseSensitivityInfoHolder.caseSensitive)

  /**
   * In split mode we operate [com.intellij.openapi.vcs.RemoteFilePath], so it's always `filePath.isNonLocal == true`.
   * However, it still makes sense to try to show relative paths in the UI.
   */
  private fun shouldHandleAsNonLocal(filePath: FilePath): Boolean = AppModeAssertions.isMonolith() && filePath.isNonLocal

  private fun getRelativePathToUserHome(filePath: FilePath): @NlsSafe @SystemDependent String =
    if (AppModeAssertions.isMonolith()) FileUtil.getLocationRelativeToUserHome(getSystemDependentPath(filePath.path)) else filePath.path

  private fun getSystemDependentPath(path: String): @NlsSafe @SystemDependent String =
    if (AppModeAssertions.isMonolith()) FileUtil.toSystemDependentName(path) else path
}