// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.log.command

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.execution.ParametersListUtil
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.data.DataPack
import com.intellij.vcs.log.data.VcsLogStorage
import com.intellij.vcs.log.graph.PermanentGraph
import com.intellij.vcs.log.visible.CommitCountStage
import com.intellij.vcs.log.visible.VcsLogFilterer
import com.intellij.vcs.log.visible.VisiblePack
import com.intellij.vcs.log.visible.isAll
import git4idea.GitVcs
import git4idea.history.GitLogUtil
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSet

class GitLogCommandFilterer(private val project: Project,
                            private val storage: VcsLogStorage) : VcsLogFilterer {
  override fun filter(dataPack: DataPack,
                      oldVisiblePack: VisiblePack,
                      sortType: PermanentGraph.SortType,
                      filters: VcsLogFilterCollection,
                      commitCount: CommitCountStage): Pair<VisiblePack, CommitCountStage> {
    val gitRoots = dataPack.logProviders.filterValues { it.supportedVcs == GitVcs.getKey() }.keys
    val commandFilter = filters[GitLogCommandFilter.KEY]

    val (matchingCommits, canRequestMore) = if (commandFilter != null)
      collectMatchingCommits(gitRoots, commandFilter, commitCount)
    else Pair(null, false)

    val visibleGraph = dataPack.permanentGraph.createVisibleGraph(sortType, null, matchingCommits)
    return Pair(VisiblePack(dataPack, visibleGraph, canRequestMore, filters), commitCount)
  }

  private fun collectMatchingCommits(roots: Set<VirtualFile>,
                                     commandFilter: GitLogCommandFilter,
                                     commitCount: CommitCountStage): Pair<IntSet, Boolean> {
    val command = prepareCommand(commandFilter, commitCount)

    val result = IntOpenHashSet()
    var canRequestMore = false

    for (root in roots) {
      val commits = IntOpenHashSet()
      GitLogUtil.readTimedCommits(project, root, command, null, null) {
        commits.add(storage.getCommitIndex(it.id, root))
      }
      result.addAll(commits)
      canRequestMore = canRequestMore || (commits.size >= commitCount.count && !commandFilter.hasMaxCount())
    }
    return Pair(result, canRequestMore)
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

private const val MAX_COUNT = "--max-count="