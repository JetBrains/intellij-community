// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index.vfs

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vcs.impl.VcsBaseContentProvider
import com.intellij.openapi.vcs.impl.VcsFileStatusProvider.createBaseContent
import com.intellij.openapi.vfs.VirtualFile
import git4idea.GitContentRevision
import git4idea.GitRevisionNumber
import git4idea.index.*
import git4idea.repo.GitRepositoryManager

class GitIndexVirtualFileBaseContentProvider(private val project: Project) : VcsBaseContentProvider {

  override fun isSupported(file: VirtualFile): Boolean = file is GitIndexVirtualFile

  override fun getBaseRevision(file: VirtualFile): VcsBaseContentProvider.BaseContent? {
    val indexFile = file as? GitIndexVirtualFile ?: return null

    val status = GitStageTracker.getInstance(project).status(indexFile) ?: return null
    if (!status.has(ContentVersion.HEAD)) return null

    val headPath = status.path(ContentVersion.HEAD)
    val currentRevisionNumber = currentRevisionNumber(indexFile.root) ?: return null
    return createBaseContent(project, GitContentRevision.createRevision(headPath, currentRevisionNumber, project))
  }

  private fun currentRevisionNumber(root: VirtualFile): VcsRevisionNumber? {
    val currentRevision = GitRepositoryManager.getInstance(project).getRepositoryForRootQuick(root)?.currentRevision
    return currentRevision?.let { GitRevisionNumber(it) }
  }
}