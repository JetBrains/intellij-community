// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.dashboard

import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.impl.VcsProjectLog
import com.intellij.vcs.log.util.exclusiveCommits
import com.intellij.vcs.log.util.findBranch
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import git4idea.GitBranch
import git4idea.GitLocalBranch
import git4idea.branch.GitBranchIncomingOutgoingManager
import git4idea.branch.GitBranchType
import git4idea.branch.IncomingOutgoingState
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import git4idea.ui.branch.GitBranchManager
import it.unimi.dsi.fastutil.ints.IntSet

internal object BranchesDashboardUtil {

  fun getLocalBranches(project: Project, rootsToFilter: Set<VirtualFile>?): Set<BranchInfo> {
    val localMap = mutableMapOf<GitBranch, MutableSet<GitRepository>>()
    for (repo in GitRepositoryManager.getInstance(project).repositories) {
      if (rootsToFilter != null && !rootsToFilter.contains(repo.root)) continue

      for (branch in repo.branches.localBranches) {
        localMap.computeIfAbsent(branch) { hashSetOf() }.add(repo)
      }
      val currentBranch = repo.currentBranch
      if (currentBranch != null) {
        localMap.computeIfAbsent(currentBranch) { hashSetOf() }.add(repo)
      }
    }
    val gitBranchManager = project.service<GitBranchManager>()
    val incomingOutgoingManager = GitBranchIncomingOutgoingManager.getInstance(project)
    val local = localMap.map { (branch, repos) ->
      BranchInfo(branch, true, repos.any { it.currentBranch == branch },
                 repos.any { gitBranchManager.isFavorite(GitBranchType.LOCAL, it, branch.name) },
                 incomingOutgoingManager.getIncomingOutgoingState(repos, branch as GitLocalBranch),
                 repos.toList())
    }.toHashSet()

    return local
  }

  fun getRemoteBranches(project: Project, rootsToFilter: Set<VirtualFile>?): Set<BranchInfo> {
    val remoteMap = mutableMapOf<GitBranch, MutableList<GitRepository>>()
    for (repo in GitRepositoryManager.getInstance(project).repositories) {
      if (rootsToFilter != null && !rootsToFilter.contains(repo.root)) continue

      for (remoteBranch in repo.branches.remoteBranches) {
        remoteMap.computeIfAbsent(remoteBranch) { mutableListOf() }.add(repo)
      }
    }
    val gitBranchManager = project.service<GitBranchManager>()
    return remoteMap.map { (branch, repos) ->
      BranchInfo(branch, false, false,
                 repos.any { gitBranchManager.isFavorite(GitBranchType.REMOTE, it, branch.name) },
                 IncomingOutgoingState.EMPTY,
                 repos)
    }.toHashSet()
  }

  fun checkIsMyBranchesSynchronously(log: VcsProjectLog,
                                     branchesToCheck: Collection<BranchInfo>,
                                     indicator: ProgressIndicator): Set<BranchInfo> {
    val myCommits = findMyCommits(log)
    if (myCommits.isEmpty()) return emptySet()

    indicator.isIndeterminate = false
    val myBranches = hashSetOf<BranchInfo>()
    for ((step, branch) in branchesToCheck.withIndex()) {
      indicator.fraction = step.toDouble() / branchesToCheck.size

      for (repo in branch.repositories) {
        indicator.checkCanceled()
        if (isMyBranch(log, branch.branchName, repo, myCommits)) {
          myBranches.add(branch)
        }
      }
    }

    return myBranches
  }



  private fun findMyCommits(log: VcsProjectLog): Set<Int> {
    val filterByMe = VcsLogFilterObject.fromUserNames(listOf(VcsLogFilterObject.ME), log.dataManager!!)
    return log.dataManager!!.index.dataGetter!!.filter(listOf(filterByMe))
  }

  private fun isMyBranch(log: VcsProjectLog,
                         branchName: String,
                         repo: GitRepository,
                         myCommits: Set<Int>): Boolean {
    // branch is "my" if all its exclusive commits are made by me
    val exclusiveCommits = findExclusiveCommits(log, branchName, repo) ?: return false
    if (exclusiveCommits.isEmpty()) return false

    for (commit in exclusiveCommits) {
      if (!myCommits.contains(commit)) {
        return false
      }
    }

    return true
  }

  private fun findExclusiveCommits(log: VcsProjectLog, branchName: String, repo: GitRepository): IntSet? {
    val dataPack = log.dataManager!!.dataPack

    val ref = dataPack.findBranch(branchName, repo.root) ?: return null
    if (!ref.type.isBranch) return null

    return dataPack.exclusiveCommits(ref, dataPack.refsModel, log.dataManager!!.storage)
  }
}
