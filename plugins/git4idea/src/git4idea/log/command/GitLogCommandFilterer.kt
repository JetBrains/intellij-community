// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.log.command

import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.execution.ParametersListUtil
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.data.DataPack
import com.intellij.vcs.log.data.VcsLogProgress
import com.intellij.vcs.log.data.VcsLogStorage
import com.intellij.vcs.log.graph.PermanentGraph
import com.intellij.vcs.log.util.RevisionCollectorTask
import com.intellij.vcs.log.visible.CommitCountStage
import com.intellij.vcs.log.visible.VcsLogFilterer
import com.intellij.vcs.log.visible.VisiblePack
import com.intellij.vcs.log.visible.isAll
import git4idea.GitVcs
import git4idea.history.GitLogUtil
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSet

class GitLogCommandFilterer(private val project: Project,
                            private val storage: VcsLogStorage,
                            private val progress: VcsLogProgress) : VcsLogFilterer, Disposable {

  private var collectorTask: GitLogRevisionCollectorTask? = null

  override fun filter(dataPack: DataPack,
                      oldVisiblePack: VisiblePack,
                      graphOptions: PermanentGraph.Options,
                      filters: VcsLogFilterCollection,
                      commitCount: CommitCountStage): Pair<VisiblePack, CommitCountStage> {
    val gitRoots = dataPack.logProviders.filterValues { it.supportedVcs == GitVcs.getKey() }.keys
    val commandFilter = filters[GitLogCommandFilter.KEY]

    val (matchingCommits, canRequestMore) = if (commandFilter != null)
      collectMatchingCommits(gitRoots, commandFilter, commitCount)
    else Pair(null, false)

    val visibleGraph = dataPack.permanentGraph.createVisibleGraph(graphOptions, null, matchingCommits)
    return Pair(VisiblePack(dataPack, visibleGraph, canRequestMore, filters), commitCount)
  }

  private fun collectMatchingCommits(roots: Set<VirtualFile>,
                                     commandFilter: GitLogCommandFilter,
                                     commitCount: CommitCountStage): Pair<IntSet, Boolean> {
    if (commitCount.isInitial && roots.size > 1) {
      cancelTask(false)

      val result = IntOpenHashSet()
      val canRequestMore = collectRevisions(project, storage, commandFilter, roots, commitCount, result::add)
      return Pair(result, canRequestMore)
    }

    val (revisions, isDone) = startTask(commandFilter, roots, commitCount.isInitial).waitForRevisions(500)
    return Pair(IntOpenHashSet(revisions), !isDone)
  }

  private fun startTask(commandFilter: GitLogCommandFilter,
                        roots: Collection<VirtualFile>,
                        isInitial: Boolean): RevisionCollectorTask<Int> {
    val oldTask = collectorTask
    if (oldTask != null && oldTask.commandFilter == commandFilter && !oldTask.isCancelled && !isInitial) return oldTask

    cancelTask(false)
    val progressIndicator = progress.createProgressIndicator(VcsLogProgress.ProgressKey("git log for $commandFilter"))
    val newTask = GitLogRevisionCollectorTask(project, progressIndicator, commandFilter, roots, storage)
    collectorTask = newTask
    return newTask
  }

  private fun cancelTask(wait: Boolean) {
    collectorTask?.cancel(wait)
    collectorTask = null
  }

  private class GitLogRevisionCollectorTask(project: Project, indicator: ProgressIndicator,
                                            val commandFilter: GitLogCommandFilter,
                                            val roots: Collection<VirtualFile>,
                                            val storage: VcsLogStorage) :
    RevisionCollectorTask<Int>(project, indicator, null) {

    override fun collectRevisions(consumer: (Int) -> Unit) {
      collectRevisions(project, storage, commandFilter, roots, CommitCountStage.ALL, consumer)
    }
  }

  companion object {
    fun collectRevisions(project: Project,
                         storage: VcsLogStorage,
                         commandFilter: GitLogCommandFilter,
                         roots: Collection<VirtualFile>,
                         commitCount: CommitCountStage,
                         consumer: (Int) -> Unit): Boolean {
      var canRequestMore = false
      val command = prepareCommand(commandFilter, commitCount)
      for (root in roots) {
        var count = 0
        GitLogUtil.readTimedCommits(project, root, command, null, null) {
          consumer(storage.getCommitIndex(it.id, root))
          count++
        }
        canRequestMore = canRequestMore || (count >= commitCount.count && !commandFilter.hasMaxCount())
      }
      return canRequestMore
    }

    private fun prepareCommand(commandFilter: GitLogCommandFilter, commitCount: CommitCountStage): List<String> {
      val command = ParametersListUtil.parse(commandFilter.command, false, true).toMutableList()
      command.removeIf { it.startsWith("--pretty") }
      if (!commitCount.isAll() && !commandFilter.hasMaxCount()) {
        command.add(0, "$MAX_COUNT${commitCount.count}")
      }
      return command
    }

    private fun GitLogCommandFilter.hasMaxCount() = command.contains(MAX_COUNT)
  }

  override fun dispose() {
    cancelTask(true)
  }
}

private const val MAX_COUNT = "--max-count="