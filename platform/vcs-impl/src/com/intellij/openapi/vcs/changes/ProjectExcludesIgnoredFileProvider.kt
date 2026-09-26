// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsApplicationSettings
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vfs.VirtualFileManager

internal class ProjectExcludesIgnoredFileProvider : IgnoredFileProvider {

  override fun isIgnoredFile(project: Project, filePath: FilePath) =
    VcsApplicationSettings.getInstance().MARK_EXCLUDED_AS_IGNORED &&
    !Registry.`is`("ide.hide.excluded.files") &&
    filePath.virtualFile?.let { file ->
      val projectFileIndex = ProjectFileIndex.getInstance(project)
      // should check only excluded files but not already ignored (e.g., ".git" directory)
      (projectFileIndex.isExcluded(file) && !projectFileIndex.isUnderIgnored(file))
    } ?: false

  override fun getIgnoredFiles(project: Project) =
    if (!VcsApplicationSettings.getInstance().MARK_EXCLUDED_AS_IGNORED) emptySet()
    else ProjectManagerEx.getInstanceEx().getAllExcludedUrls(project)
      .mapNotNull { url ->
        VirtualFileManager.getInstance().findFileByUrl(url)?.let {
          IgnoredBeanFactory.ignoreFile(it, project)
        }
      }
      .toSet()

  override fun getIgnoredGroupDescription() = VcsBundle.message("changes.project.exclude.paths")
}
