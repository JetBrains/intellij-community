// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ignore

import com.intellij.dvcs.ignore.VcsIgnoredFilesHolderBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangesViewRefresher
import com.intellij.openapi.vcs.changes.VcsIgnoredFilesHolder
import git4idea.GitVcs
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager

class GitIgnoredFilesHolder(val project: Project, val manager: GitRepositoryManager)
  : VcsIgnoredFilesHolderBase<GitRepository>(manager) {
  override fun getHolder(repository: GitRepository) = repository.ignoredFilesHolder

  override fun copy() = GitIgnoredFilesHolder(project, manager)

  class Provider(val project: Project, val manager: GitRepositoryManager) : VcsIgnoredFilesHolder.Provider, ChangesViewRefresher {

    private val gitVcs = GitVcs.getInstance(project)

    override fun getVcs() = gitVcs

    override fun createHolder() = GitIgnoredFilesHolder(project, manager)

    override fun refresh(project: Project) {
      manager.repositories.forEach { r -> r.ignoredFilesHolder.startRescan() }
    }
  }
}