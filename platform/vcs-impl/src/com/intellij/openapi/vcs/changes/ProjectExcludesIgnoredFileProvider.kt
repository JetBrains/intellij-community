// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.impl.DirectoryIndexExcludePolicy
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ui.ChangesComparator
import com.intellij.openapi.vfs.VirtualFileManager

class ProjectExcludesIgnoredFileProvider : IgnoredFileProvider {

  override fun isIgnoredFile(project: Project, filePath: FilePath) =
    ChangeListManagerImpl.getInstanceImpl(project).ignoredFilesComponent.isIgnoredFile(filePath)

  override fun getIgnoredFiles(project: Project) = getProjectExcludePathsRelativeTo(project)

  override fun getIgnoredGroupDescription() = "Project exclude paths"

  private fun getProjectExcludePathsRelativeTo(project: Project): Set<IgnoredFileDescriptor> {
    val excludes = sortedSetOf(ChangesComparator.getVirtualFileComparator(false))

    val fileIndex = ProjectFileIndex.SERVICE.getInstance(project)

    for (policy in DirectoryIndexExcludePolicy.EP_NAME.getExtensions(project)) {
      for (url in policy.excludeUrlsForProject) {
        val file = VirtualFileManager.getInstance().findFileByUrl(url)
        if (file != null) {
          excludes.add(file)
        }
      }
    }

    for (module in ModuleManager.getInstance(project).modules) {
      if (module.isDisposed) continue

      for (excludeRoot in ModuleRootManager.getInstance(module).excludeRoots) {
        if (!fileIndex.isExcluded(excludeRoot)) {
          //root is included into some inner module so it shouldn't be ignored
          continue
        }
        excludes.add(excludeRoot)
      }
    }

    return excludes.map { file -> IgnoredBeanFactory.ignoreFile(file, project) }.toSet()
  }
}