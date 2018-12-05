// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ignore

import com.intellij.dvcs.ignore.VcsRepositoryIgnoredFilesHolderBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.LocalFileSystem
import git4idea.commands.Git
import git4idea.repo.GitRepository
import java.util.*

class GitRepositoryIgnoredFilesHolder(private val project: Project,
                                      repository: GitRepository,
                                      private val git: Git)
  : VcsRepositoryIgnoredFilesHolderBase<GitRepository>(repository, "GitIgnoreUpdate", "gitRescanIgnored") {

  override fun requestIgnored(ignoreFilePath: String?) =
    HashSet(git.ignoredFiles(project, repository.root, findIgnoreFileVcsRootOrDefault(ignoreFilePath)))

  private fun findIgnoreFileVcsRootOrDefault(ignoreFilePath: String?) =
    if (ignoreFilePath != null) {
      LocalFileSystem.getInstance().findFileByPath(ignoreFilePath)?.parent ?: repository.root
    }
    else {
      repository.root
    }

  override fun scanTurnedOff() = !Registry.`is`("git.process.ignored")
}