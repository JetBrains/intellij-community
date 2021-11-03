// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github

import com.intellij.ide.actions.DumbAwareCopyPathProvider
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.VirtualFile
import git4idea.GitUtil
import org.jetbrains.plugins.github.util.GHProjectRepositoriesManager

class GithubCopyPathProvider: DumbAwareCopyPathProvider() {
  override fun getPathToElement(project: Project, virtualFile: VirtualFile?, editor: Editor?): String? {
    if (virtualFile == null) return null

    val fileStatus = ChangeListManager.getInstance(project).getStatus(virtualFile);
    if (fileStatus == FileStatus.UNKNOWN || fileStatus == FileStatus.ADDED || fileStatus == FileStatus.IGNORED) return null

    val repository = GitUtil.getRepositoryManager(project).getRepositoryForFileQuick(virtualFile)
    if (repository == null) return null

    val accessibleRepositories = project.service<GHProjectRepositoriesManager>().findKnownRepositories(repository)
    if (accessibleRepositories.isEmpty()) return null

    val refs = accessibleRepositories
      .mapNotNull { GHPathUtil.getFileURL(repository, it.ghRepositoryCoordinates, virtualFile, editor) }
      .distinct()

    return if (refs.isNotEmpty()) refs.joinToString("\n") else null
  }
}