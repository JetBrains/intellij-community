// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index.lst

import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ex.LocalLineStatusTracker
import com.intellij.openapi.vcs.impl.LocalLineStatusTrackerProvider
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil
import git4idea.index.*
import git4idea.index.vfs.GitIndexVirtualFileCache
import git4idea.repo.GitRepositoryManager

class GitStageLineStatusTrackerProvider : LocalLineStatusTrackerProvider {
  override fun isMyTracker(tracker: LocalLineStatusTracker<*>): Boolean = tracker is GitStageLineStatusTracker

  override fun isTrackedFile(project: Project, file: VirtualFile): Boolean {
    if (!file.isInLocalFileSystem) return false
    if (!isStageAvailable(project)) return false
    if (!stageLineStatusTrackerRegistryOption().asBoolean()) return false

    val repository = GitRepositoryManager.getInstance(project).getRepositoryForFileQuick(file)
    return repository != null
  }

  override fun createTracker(project: Project, file: VirtualFile): LocalLineStatusTracker<*>? {
    val stageTracker = GitStageTracker.getInstance(project)
    val status = stageTracker.status(file) ?: return null

    if (!status.isTracked() ||
        !status.has(ContentVersion.HEAD) ||
        !status.has(ContentVersion.STAGED) ||
        !status.has(ContentVersion.LOCAL)) return null

    val root = VcsUtil.getVcsRootFor(project, file) ?: return null
    val document = FileDocumentManager.getInstance().getDocument(file) ?: return null

    val indexFileCache = project.service<GitIndexVirtualFileCache>()
    val indexFile = indexFileCache.get(root, status.path(ContentVersion.STAGED))
    val indexDocument = FileDocumentManager.getInstance().getDocument(indexFile) ?: return null

    return GitStageLineStatusTracker(project, file, document, indexDocument)
  }
}