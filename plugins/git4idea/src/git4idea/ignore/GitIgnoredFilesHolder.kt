// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ignore

import com.intellij.dvcs.ignore.VcsIgnoredFilesHolderBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.VcsManagedFilesHolder
import git4idea.GitVcs
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager

class GitIgnoredFilesHolder(val manager: GitRepositoryManager)
  : VcsIgnoredFilesHolderBase<GitRepository>(manager) {
  override fun getHolder(repository: GitRepository) = repository.ignoredFilesHolder

  override fun copy() = GitIgnoredFilesHolder(manager)

  class Provider(project: Project) : VcsManagedFilesHolder.Provider {
    private val gitVcs = GitVcs.getInstance(project)
    private val manager = GitRepositoryManager.getInstance(project)

    override fun getVcs() = gitVcs

    override fun createHolder() = GitIgnoredFilesHolder(manager)
  }
}