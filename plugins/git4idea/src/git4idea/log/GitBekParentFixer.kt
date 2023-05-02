// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.log

import com.intellij.platform.diagnostic.telemetry.TelemetryTracer
import com.intellij.vcs.log.data.util.VCS
import com.intellij.platform.diagnostic.telemetry.impl.computeWithSpan
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.ContainerUtil
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.TimedVcsCommit
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsLogTextFilter
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.data.index.IndexDataGetter
import com.intellij.vcs.log.impl.VcsProjectLog
import com.intellij.vcs.log.util.BekUtil
import git4idea.history.GitLogUtil
import java.util.regex.Pattern

internal class GitBekParentFixer private constructor(private val incorrectCommits: Set<Hash>) {

  fun fixCommit(commit: TimedVcsCommit): TimedVcsCommit {
    return if (!incorrectCommits.contains(commit.id)) commit
    else object : TimedVcsCommit by commit {
      override fun getParents(): List<Hash> = ContainerUtil.reverse(commit.parents)
    }
  }

  companion object {
    private val LOG = Logger.getInstance(GitBekParentFixer::class.java)

    @JvmStatic
    fun prepare(project: Project, root: VirtualFile): GitBekParentFixer {
      if (isEnabled()) {
        try {
          return GitBekParentFixer(getIncorrectCommits(project, root))
        }
        catch (e: VcsException) {
          LOG.warn("Could not find incorrect merges ", e)
        }
      }
      return GitBekParentFixer(emptySet())
    }

    @JvmStatic
    fun fixCommits(commits: List<VcsCommitMetadata>): List<VcsCommitMetadata> {
      if (!isEnabled()) return commits

      return commits.map map@{ commit ->
        if (commit.parents.size <= 1) return@map commit
        if (!MAGIC_FILTER.matches(commit.fullMessage)) return@map commit
        return@map object : VcsCommitMetadata by commit {
          override fun getParents(): List<Hash> = ContainerUtil.reverse(commit.parents)
        }
      }
    }
  }
}

private fun isEnabled() = BekUtil.isBekEnabled() && Registry.`is`("git.log.fix.merge.commits.parents.order")

private const val MAGIC_REGEX = "^Merge remote(\\-tracking)? branch '.*/(.*)'( into \\2)?$"
private val MAGIC_FILTER = object : VcsLogTextFilter {
  val pattern = Pattern.compile(MAGIC_REGEX, Pattern.MULTILINE)

  override fun matchesCase(): Boolean {
    return false
  }

  override fun isRegex(): Boolean {
    return false
  }

  override fun getText(): String {
    return "Merge remote"
  }

  override fun matches(message: String): Boolean {
    return pattern.matcher(message).find(0)
  }
}

@Throws(VcsException::class)
fun getIncorrectCommits(project: Project, root: VirtualFile): Set<Hash> {
  val dataManager = VcsProjectLog.getInstance(project).dataManager
  val dataGetter = dataManager?.index?.dataGetter
  if (dataGetter == null || !dataManager.index.isIndexed(root)) {
    return getIncorrectCommitsFromGit(project, root)
  }
  return getIncorrectCommitsFromIndex(dataManager = dataManager, dataGetter = dataGetter, root = root)
}

private fun getIncorrectCommitsFromIndex(dataManager: VcsLogData,
                                         dataGetter: IndexDataGetter,
                                         root: VirtualFile): Set<Hash> {
  computeWithSpan(TelemetryTracer.getInstance().getTracer(VCS), "getting incorrect merges from index") { span ->
    span.setAttribute("rootName", root.name)

    val commits = dataGetter.filter(listOf(MAGIC_FILTER)).asSequence()
    return commits.map { dataManager.storage.getCommitId(it)!! }.filter { it.root == root }.mapTo(hashSetOf()) { it.hash }
  }
}

@Throws(VcsException::class)
fun getIncorrectCommitsFromGit(project: Project, root: VirtualFile): MutableSet<Hash> {
  return computeWithSpan(TelemetryTracer.getInstance().getTracer(VCS), "getting incorrect merges from git") { span ->
    span.setAttribute("rootName", root.name)

    val filterParameters = mutableListOf<String>()
    filterParameters.addAll(GitLogUtil.LOG_ALL)
    filterParameters.add("--merges")

    GitLogProvider.appendTextFilterParameters(MAGIC_REGEX, true, false, filterParameters)

    val result = mutableSetOf<Hash>()
    GitLogUtil.readTimedCommits(project, root, filterParameters, {}, {}, { commit -> result.add(commit.id) })
    return@computeWithSpan result
  }
}