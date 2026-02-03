// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.statistics

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitHandlerInputProcessorUtil
import git4idea.commands.GitLineHandler
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import java.time.LocalDateTime
import java.time.Period
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class GitCommitterCounter(periods: List<Period>,
                          private val additionalGitParameters: List<String> = emptyList<String>(),
                          val collectCommitCount: Boolean = false) {
  private val thresholds: List<String>

  init {
    val now = LocalDateTime.now()
    thresholds = periods.map { (now - it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) }
  }

  fun calculateWithGit(project: Project, repo: GitRepository): List<CommitsSummary> {
    val result = mutableListOf<CommitsSummary>()
    thresholds.forEachIndexed { i, sinceDate ->
      result.add(calculateWithGit(project, repo, sinceDate))
    }
    return result
  }

  private fun calculateWithGit(project: Project, repo: GitRepository, since: String): CommitsSummary {
    val handler = GitLineHandler(project, repo.root, GitCommand.SHORTLOG).apply {
      setSilent(true)
      addParameters("-s", "--since", since)
      addParameters(additionalGitParameters)
      setInputProcessor(GitHandlerInputProcessorUtil.empty()) // 'git shortlog' expects stdin if called on a fresh repository
    }
    val result = Git.getInstance().runCommand(handler)
    result.throwOnError()
    var commits = 0
    if (collectCommitCount) {
      for (line in result.output) {
        val split = line.trim().split("\\W".toRegex())
        if (split.isEmpty()) throw VcsException(GitBundle.message("stats.wrong.git.shortlog.response", line))
        try {
          commits += split[0].toInt()
        }
        catch (e: NumberFormatException) {
          throw VcsException(GitBundle.message("stats.wrong.git.shortlog.response", line), e)
        }
      }
    }

    return CommitsSummary(commits, result.output.size)
  }
}

data class CommitsSummary(val commits: Int, val authors: Int)