// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ignore

import com.intellij.dvcs.repo.AsyncFilesManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.changes.ChangesViewRefresher
import com.intellij.openapi.vcs.changes.FileHolder
import com.intellij.openapi.vcs.changes.VcsIgnoredFilesHolder
import com.intellij.openapi.vcs.changes.VcsModifiableDirtyScope
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.EventDispatcher
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitUtil
import git4idea.GitVcs
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager

class GitIgnoredFilesHolder(val project: Project, val manager: GitRepositoryManager) : VcsIgnoredFilesHolder {

  private val listeners = EventDispatcher.create(AsyncFilesManagerListener::class.java)

  private val vcsIgnoredHolderMap =
    manager.repositories.associateTo(hashMapOf<GitRepository, GitRepositoryIgnoredHolder>()) { it to it.ignoredFilesHolder }

  override fun isInUpdatingMode() = vcsIgnoredHolderMap.values.any(GitRepositoryIgnoredHolder::isInUpdateMode)

  override fun notifyVcsStarted(scope: AbstractVcs<*>?) {}

  override fun addFile(file: VirtualFile) {
    findIgnoreHolderByFile(file)?.addFile(file)
  }

  override fun containsFile(file: VirtualFile) = findIgnoreHolderByFile(file)?.containsFile(file) ?: false

  override fun values() = vcsIgnoredHolderMap.flatMap { it.value.getIgnoredFiles() }

  override fun startRescan() {
    fireUpdateStarted()
    vcsIgnoredHolderMap.values.forEach { it.startRescan() }
    fireUpdateFinished()
  }

  override fun cleanAll() {
    vcsIgnoredHolderMap.clear()
  }

  override fun copy() = GitIgnoredFilesHolder(project, manager)

  override fun getType() = FileHolder.HolderType.IGNORED

  override fun cleanAndAdjustScope(scope: VcsModifiableDirtyScope) {}

  private fun findIgnoreHolderByFile(file: VirtualFile): GitRepositoryIgnoredHolder? {
    val repositoryRoot = VcsUtil.getVcsRootFor(project, file) ?: return null
    val repositoryForRoot = manager.getRepositoryForRoot(repositoryRoot) ?: return null
    return vcsIgnoredHolderMap[repositoryForRoot]
  }

  private fun fireUpdateStarted() {
    listeners.multicaster.updateStarted()
  }

  private fun fireUpdateFinished() {
    listeners.multicaster.updateFinished()
  }

  class Provider(val project: Project, val manager: GitRepositoryManager) : VcsIgnoredFilesHolder.Provider, ChangesViewRefresher {

    private val gitVcs = GitVcs.getInstance(project)

    override fun getVcs() = gitVcs

    override fun createHolder() = GitIgnoredFilesHolder(project, manager)

    override fun refresh(project: Project) {
      GitUtil.getRepositoryManager(project).repositories.forEach { r -> r.ignoredFilesHolder.startRescan() }
    }
  }
}