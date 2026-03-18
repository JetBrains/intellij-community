// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.checkin

import com.intellij.dvcs.commit.AmendCommitService
import com.intellij.dvcs.repo.isHead
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.commit.CommitToAmend
import com.intellij.vcs.log.VcsCommitMetadata
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.commit.GitRecentCommitsProvider
import git4idea.config.GitVersionSpecialty
import git4idea.repo.GitRepositoryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.NonNls

@Service(Service.Level.PROJECT)
internal class GitAmendCommitService(project: Project, scope: CoroutineScope) : AmendCommitService(project) {
  private val recentCommitsProvider = GitRecentCommitsProvider(
    project, scope,
    limit = COMMITS_LIMIT,
    userScope = GitRecentCommitsProvider.UserScope.ALL_USERS,
    stopAtFirstMergeCommit = true,
    unpublishedOnly = true
  )

  override fun isAmendCommitSupported(): Boolean = true
  override fun isAmendSpecificCommitSupported(): Boolean = Registry.`is`("git.amend.specific.commit")

  @Throws(VcsException::class)
  override fun getLastCommitMessage(root: VirtualFile): String {
    val h = GitLineHandler(project, root, GitCommand.LOG)
    h.addParameters("--max-count=1")
    h.addParameters("--encoding=UTF-8")
    h.addParameters("--pretty=format:${getCommitMessageFormatPattern()}")
    return Git.getInstance().runCommand(h).getOutputOrThrow()
  }

  override suspend fun getAmendSpecificCommitTargets(root: VirtualFile): List<CommitToAmend.Specific> =
    withContext(Dispatchers.Default) {
      val repo = GitRepositoryManager.getInstance(project).repositories.singleOrNull() ?: return@withContext emptyList()
      val commits: List<VcsCommitMetadata> = recentCommitsProvider.getRecentCommits(repo.root)

      commits
        .dropWhile { repo.isHead(it.id) } // don't include last commit
        .map { metadata ->
          CommitToAmend.Specific(metadata.id, metadata.subject)
        }
    }

  private fun getCommitMessageFormatPattern(): @NonNls String =
    if (GitVersionSpecialty.STARTED_USING_RAW_BODY_IN_FORMAT.existsIn(project)) {
      "%B"
    }
    else {
      // only message: subject + body; "%-b" means that preceding line-feeds will be deleted if the body is empty
      // %s strips newlines from subject; there is no way to work around it before 1.7.2 with %B (unless parsing some fixed format)
      "%s%n%n%-b"
    }

  companion object {
    private const val COMMITS_LIMIT: Int = 20
  }
}