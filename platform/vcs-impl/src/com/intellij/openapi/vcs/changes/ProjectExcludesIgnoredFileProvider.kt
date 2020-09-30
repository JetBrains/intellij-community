// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.impl.DirectoryIndexExcludePolicy
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsApplicationSettings
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.ui.ChangesComparator
import com.intellij.openapi.vfs.VirtualFileManager

class ProjectExcludesIgnoredFileProvider : IgnoredFileProvider {

  override fun isIgnoredFile(project: Project, filePath: FilePath) =
    VcsApplicationSettings.getInstance().MARK_EXCLUDED_AS_IGNORED &&
    !Registry.`is`("ide.hide.excluded.files") &&
    filePath.virtualFile?.let { file ->
      val projectFileIndex = ProjectFileIndex.getInstance(project)
      //should check only excluded files but not already ignored (e.g. .git directory)
      (projectFileIndex.isExcluded(file) && !projectFileIndex.isUnderIgnored(file))
    } ?: false

  override fun getIgnoredFiles(project: Project) = getProjectExcludePaths(project)

  override fun getIgnoredGroupDescription() = VcsBundle.message("changes.project.exclude.paths")

  private fun getProjectExcludePaths(project: Project): Set<IgnoredFileDescriptor> {
    if (!VcsApplicationSettings.getInstance().MARK_EXCLUDED_AS_IGNORED) return emptySet()

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
        if (runReadAction { !fileIndex.isExcluded(excludeRoot) }) {
          //root is included into some inner module so it shouldn't be ignored
          continue
        }
        excludes.add(excludeRoot)
      }
    }

    return excludes.map { file -> IgnoredBeanFactory.ignoreFile(file, project) }.toSet()
  }
}