// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl.getInstanceImpl
import com.intellij.openapi.vcs.changes.ui.ChangesComparator

class ProjectExcludesIgnoredFileProvider : IgnoredFileProvider {

  override fun isIgnoredFile(project: Project, filePath: FilePath) = getInstanceImpl(project).ignoredFilesComponent.isIgnoredFile(filePath)

  override fun getIgnoredFiles(project: Project) = getProjectExcludePathsRelativeTo(project)

  override fun getIgnoredGroupDescription() = "Project exclude paths"

  private fun getProjectExcludePathsRelativeTo(project: Project): Set<IgnoredFileDescriptor> {
    val excludes = sortedSetOf(ChangesComparator.getVirtualFileComparator(false))

    for (module in ModuleManager.getInstance(project).modules) {
      if(module.isDisposed) continue

      val roots = ModuleRootManager.getInstance(module).excludeRoots
      excludes.addAll(roots)
    }

    return excludes.map { root -> IgnoredBeanFactory.ignoreUnderDirectory(root.path, project) }.toSet()
  }
}