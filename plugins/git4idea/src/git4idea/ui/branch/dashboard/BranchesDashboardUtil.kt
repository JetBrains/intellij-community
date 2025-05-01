// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.dashboard

import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.vcs.git.shared.branch.GitInOutCountersInProject
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.util.exclusiveCommits
import com.intellij.vcs.log.util.findBranch
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import git4idea.GitBranch
import git4idea.GitLocalBranch
import git4idea.GitReference
import git4idea.GitTag
import git4idea.branch.GitBranchIncomingOutgoingManager
import git4idea.branch.GitRefType
import git4idea.repo.GitRefUtil.getCurrentTag
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitBranchManager
import git4idea.ui.branch.tree.tags
import it.unimi.dsi.fastutil.ints.IntSet

internal object BranchesDashboardUtil {
  fun getLocalBranches(project: Project, repositories: Collection<GitRepository>): Set<BranchInfo> {
    val localMap = mutableMapOf<GitLocalBranch, MutableSet<GitRepository>>()
    repositories.forEach { repo ->
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
      BranchInfo(branch, repos.any { it.currentBranch == branch },
                 isFavoriteInAnyRepo(repos, gitBranchManager, branch),
                 incomingOutgoingManager.getIncomingOutgoingState(repos, branch),
                 repos.toList())
    }.toHashSet()

    return local
  }

  fun getRemoteBranches(project: Project, repositories: Collection<GitRepository>): Set<BranchInfo> {
    val remoteMap = mutableMapOf<GitBranch, MutableList<GitRepository>>()
    repositories.forEach { repo ->
      for (remoteBranch in repo.branches.remoteBranches) {
        remoteMap.computeIfAbsent(remoteBranch) { mutableListOf() }.add(repo)
      }
    }
    val gitBranchManager = project.service<GitBranchManager>()
    return remoteMap.map { (branch, repos) ->
      BranchInfo(branch, false,
                 isFavoriteInAnyRepo(repos, gitBranchManager, branch),
                 GitInOutCountersInProject.EMPTY,
                 repos)
    }.toHashSet()
  }

  fun getTags(project: Project, repositories: Collection<GitRepository>): Set<TagInfo> {
    val tags = mutableMapOf<GitTag, MutableList<GitRepository>>()
    repositories.forEach { repo ->
      for (tag in repo.tags) {
        tags.computeIfAbsent(tag.key) { mutableListOf() }.add(repo)
      }
    }
    val gitBranchManager = project.service<GitBranchManager>()
    return tags.mapTo(mutableSetOf()) { (tag, repos) ->
      TagInfo(tag, isCurrent = repos.any { getCurrentTag(it) == tag }, isFavorite = isFavoriteInAnyRepo(repos, gitBranchManager, tag), repos)
    }
  }

  private fun isFavoriteInAnyRepo(repos: Collection<GitRepository>, gitBranchManager: GitBranchManager, ref: GitReference): Boolean {
    val refType = GitRefType.of(ref)
    return repos.any { gitBranchManager.isFavorite(refType, it, ref.name) }
  }

  fun checkIsMyBranchesSynchronously(
    logData: VcsLogData,
    branchesToCheck: Collection<BranchInfo>,
    indicator: ProgressIndicator,
  ): Set<BranchInfo> {
    val myCommits = findMyCommits(logData)
    if (myCommits.isEmpty()) return emptySet()

    indicator.isIndeterminate = false
    val myBranches = hashSetOf<BranchInfo>()
    for ((step, branch) in branchesToCheck.withIndex()) {
      indicator.fraction = step.toDouble() / branchesToCheck.size

      for (repo in branch.repositories) {
        indicator.checkCanceled()
        if (isMyBranch(logData, branch.branchName, repo, myCommits)) {
          myBranches.add(branch)
        }
      }
    }

    return myBranches
  }


  private fun findMyCommits(logData: VcsLogData): Set<Int> {
    val filterByMe = VcsLogFilterObject.fromUserNames(listOf(VcsLogFilterObject.ME), logData)
    return logData.index.dataGetter!!.filter(listOf(filterByMe))
  }

  private fun isMyBranch(
    logData: VcsLogData,
    branchName: String,
    repo: GitRepository,
    myCommits: Set<Int>,
  ): Boolean {
    // branch is "my" if all its exclusive commits are made by me
    val exclusiveCommits = findExclusiveCommits(logData, branchName, repo) ?: return false
    if (exclusiveCommits.isEmpty()) return false

    for (commit in exclusiveCommits) {
      if (!myCommits.contains(commit)) {
        return false
      }
    }

    return true
  }

  private fun findExclusiveCommits(logData: VcsLogData, branchName: String, repo: GitRepository): IntSet? {
    val dataPack = logData.dataPack

    val ref = dataPack.findBranch(branchName, repo.root) ?: return null
    if (!ref.type.isBranch) return null

    return dataPack.exclusiveCommits(ref, dataPack.refsModel, logData.storage)
  }
}
