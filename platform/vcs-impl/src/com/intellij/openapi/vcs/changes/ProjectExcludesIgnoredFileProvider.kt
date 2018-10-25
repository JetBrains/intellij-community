// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl.getInstanceImpl
import com.intellij.openapi.vcs.changes.ui.ChangesComparator
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile

class ProjectExcludesIgnoredFileProvider : IgnoredFileProvider {

  override fun isIgnoredFile(project: Project, filePath: FilePath) = getInstanceImpl(project).ignoredFilesComponent.isIgnoredFile(filePath)

  override fun getIgnoredFilesMasks(project: Project, ignoreFileRoot: VirtualFile) = getProjectExcludePathsRelativeTo(project,
                                                                                                                      ignoreFileRoot)

  override fun getMasksGroupDescription() = "Project exclude paths"

  private fun getProjectExcludePathsRelativeTo(project: Project, ignoreFileRoot: VirtualFile): Set<String> {
    val excludes = sortedSetOf(ChangesComparator.getVirtualFileComparator(false))

    for (module in ModuleManager.getInstance(project).modules) {
      val roots = ModuleRootManager.getInstance(module).excludeRoots
      excludes.addAll(roots.filter { root -> VfsUtilCore.isAncestor(ignoreFileRoot, root, false) })
    }

    return excludes.map { root -> "/" + FileUtil.getRelativePath(ignoreFileRoot.path, root.path, '/')!! + '/' }.toSet()
  }
}