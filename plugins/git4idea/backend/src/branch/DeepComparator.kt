// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.branch

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.platform.vcs.impl.shared.telemetry.VcsScope
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.vcs.log.VcsRef
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.data.VcsLogGraphData
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.util.findBranch
import com.intellij.vcs.log.util.subgraphDifference
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepository
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSet
import org.jetbrains.annotations.NonNls

/**
 * This class is responsible for comparing branches via VCS Log indexes or with `git cherry` operation
 * to find commits already cherry-picked from [comparedBranch] to target (upstream) branch.
 * The comparison is not just by commit hash, but also by commit message and author and content.
 *
 * @see [com.intellij.vcs.log.data.index.IndexDataGetter]
 */
internal class DeepComparator(
  private val project: Project,
  private val vcsLogData: VcsLogData,
  private val dataPack: VcsLogGraphData?,
  private val repositoriesWithTargetBranches: Map<GitRepository, String>,
  private val comparedBranch: String,
) {

  data class ComparatorResult(val nonPickedCommits: IntSet, val exception: VcsException?)

  private val storage get() = vcsLogData.storage

  val collectedNonPickedCommits = IntOpenHashSet()
  var exception: VcsException? = null
    private set

  @RequiresBackgroundThread
  fun compare(): ComparatorResult {
    try {
      repositoriesWithTargetBranches.forEach { (repo, targetBranch) ->
        val commits = if (Registry.`is`("git.log.use.index.for.find.picked.commits")) {
          if (Registry.`is`("git.log.fast.find.picked.commits")) {
            getCommitsByIndexFast(repo.root, comparedBranch, targetBranch)
            ?: getCommitsByIndexReliable(repo.root, comparedBranch, targetBranch)
          }
          else {
            getCommitsByIndexReliable(repo.root, comparedBranch, targetBranch)
          }
        }
        else {
          getCommitsByPatch(repo.root, comparedBranch, targetBranch)
        }
        collectedNonPickedCommits.addAll(commits)
      }
    }
    catch (e: VcsException) {
      LOG.warn(e)
      exception = e
    }
    return ComparatorResult(collectedNonPickedCommits, exception)
  }

  @Throws(VcsException::class)
  private fun getCommitsByPatch(
    root: VirtualFile,
    targetBranch: String,
    sourceBranch: String,
  ): IntSet {
    return recordSpan(root, "Getting non picked commits with git") {
      getCommitsFromGit(root, targetBranch, sourceBranch)
    }
  }

  @Throws(VcsException::class)
  private fun getCommitsByIndexReliable(root: VirtualFile, sourceBranch: String, targetBranch: String): IntSet {
    val resultFromGit = getCommitsByPatch(root, targetBranch, sourceBranch)
    if (dataPack == null || !dataPack.isFull) return resultFromGit

    val resultFromIndex = recordSpan(root, "Getting non picked commits with index reliable") {
      val sourceBranchRef = dataPack.refsModel.findBranch(sourceBranch, root) ?: return@recordSpan resultFromGit
      val targetBranchRef = dataPack.refsModel.findBranch(targetBranch, root) ?: return@recordSpan resultFromGit
      getCommitsFromIndex(dataPack, root, sourceBranchRef, targetBranchRef, resultFromGit, true)
    }

    return resultFromIndex ?: resultFromGit
  }

  private fun getCommitsByIndexFast(root: VirtualFile, sourceBranch: String, targetBranch: String): IntSet? {
    if (!vcsLogData.index.isIndexed(root) || dataPack == null || !dataPack.isFull) return null

    return recordSpan(root, "Getting non picked commits with index fast") {
      val sourceBranchRef = dataPack.refsModel.findBranch(sourceBranch, root) ?: return@recordSpan null
      val targetBranchRef = dataPack.refsModel.findBranch(targetBranch, root) ?: return@recordSpan null
      val sourceBranchCommits = dataPack.subgraphDifference(sourceBranchRef, targetBranchRef, storage) ?: return@recordSpan null
      getCommitsFromIndex(dataPack, root, sourceBranchRef, targetBranchRef, sourceBranchCommits, false)
    }
  }

  @Throws(VcsException::class)
  private fun getCommitsFromGit(
    root: VirtualFile,
    targetBranch: String,
    comparedBranch: String,
  ): IntSet {
    val handler = GitLineHandler(project, root, GitCommand.CHERRY)
    handler.addParameters(targetBranch, comparedBranch) // upstream - target branch; head - compared branch

    val pickedCommits = IntOpenHashSet()
    handler.addLineListener { l, _ ->
      var line = l
      // + 645caac042ff7fb1a5e3f7d348f00e9ceea5c317
      // - c3b9b90f6c26affd7e597ebf65db96de8f7e5860
      if (line.startsWith("+")) {
        try {
          line = line.substring(2).trim { it <= ' ' }
          val firstSpace = line.indexOf(' ')
          if (firstSpace > 0) {
            line = line.substring(0, firstSpace) // safety-check: take just the first word for sure
          }
          pickedCommits.add(storage.getCommitIndex(HashImpl.build(line), root))
        }
        catch (e: Exception) {
          LOG.error("Couldn't parse line [$line]", e)
        }
      }
    }
    Git.getInstance().runCommandWithoutCollectingOutput(handler).throwOnError()
    return pickedCommits
  }

  private fun getCommitsFromIndex(
    dataPack: VcsLogGraphData?, root: VirtualFile,
    sourceBranchRef: VcsRef, targetBranchRef: VcsRef,
    sourceBranchCommits: IntSet, reliable: Boolean,
  ): IntSet? {
    if (dataPack == null) return null
    if (sourceBranchCommits.isEmpty()) return sourceBranchCommits
    if (!vcsLogData.index.isIndexed(root)) return null

    val dataGetter = vcsLogData.index.dataGetter ?: return null

    val targetBranchCommits = dataPack.subgraphDifference(targetBranchRef, sourceBranchRef, storage) ?: return null
    if (targetBranchCommits.isEmpty()) return sourceBranchCommits

    val match = dataGetter.match(root, sourceBranchCommits, targetBranchCommits, reliable)
    sourceBranchCommits.removeAll(match)
    if (!match.isEmpty()) {
      LOG.debug("Using index, detected ${match.size} commits in ${sourceBranchRef.name}#${root.name}" +
                " that were picked to the target branch" +
                (if (reliable) " with different patch id but matching cherry-picked suffix"
                else " with matching author, author time and message"))
    }

    return sourceBranchCommits
  }

  companion object {
    private val LOG = Logger.getInstance(DeepComparator::class.java)
  }

  private inline fun <R> recordSpan(root: VirtualFile, @NonNls actionName: String, block: () -> R): R {
    return TelemetryManager.getInstance().getTracer(VcsScope).spanBuilder(actionName).use { span ->
      span.setAttribute("rootName", root.name)
      block()
    }
  }
}
