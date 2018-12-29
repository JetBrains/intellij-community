// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ignore

import com.intellij.dvcs.ignore.VcsRepositoryIgnoredFilesHolderBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.FilePath
import git4idea.commands.Git
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager

class GitRepositoryIgnoredFilesHolder(private val project: Project,
                                      repository: GitRepository,
                                      repositoryManager: GitRepositoryManager,
                                      private val git: Git)
  : VcsRepositoryIgnoredFilesHolderBase<GitRepository>(repository, repositoryManager, "GitIgnoreUpdate", "gitRescanIgnored") {

  override fun requestIgnored(paths: Collection<FilePath>?) =
    HashSet(git.ignoredFiles(project, repository.root, paths))

  override fun scanTurnedOff() = !Registry.`is`("git.process.ignored")
}