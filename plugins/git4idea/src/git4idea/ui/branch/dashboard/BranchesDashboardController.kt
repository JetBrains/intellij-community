// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ui.branch.dashboard

import com.intellij.dvcs.branch.DvcsBranchManager
import com.intellij.dvcs.branch.DvcsBranchManager.DvcsBranchManagerListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.ThreeState
import com.intellij.vcs.log.data.DataPackChangeListener
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.impl.VcsProjectLog
import git4idea.branch.GitBranchType
import git4idea.ui.branch.GitBranchManager
import kotlin.properties.Delegates

internal class BranchesDashboardController(private val project: Project,
                                           private val ui: BranchesDashboardUi) : Disposable {

  private val changeListener = DataPackChangeListener { ui.updateBranchesTree(false) }

  val localBranches = hashSetOf<BranchInfo>()
  val remoteBranches = hashSetOf<BranchInfo>()
  var showOnlyMy: Boolean by Delegates.observable(false) { _, old, new -> if (old != new) updateBranchesIsMyState() }

  init {
    Disposer.register(ui, this)
    project.messageBus.connect(this).subscribe(DvcsBranchManager.DVCS_BRANCH_SETTINGS_CHANGED, DvcsBranchManagerListener {
      updateBranchesIsFavoriteState()
    })
  }

  override fun dispose() {
    localBranches.clear()
    remoteBranches.clear()
  }

  fun checkForBranchesUpdate(): Boolean {
    val newLocalBranches = BranchesDashboardUtil.getLocalBranches(project)
    val newRemoteBranches = BranchesDashboardUtil.getRemoteBranches(project)
    val localChanged = localBranches.size != newLocalBranches.size || !localBranches.containsAll(newLocalBranches)
    val remoteChanged = remoteBranches.size != newRemoteBranches.size || !remoteBranches.containsAll(newRemoteBranches)

    if (localChanged) {
      localBranches.clear()
      localBranches.addAll(newLocalBranches)
    }
    if (remoteChanged) {
      remoteBranches.clear()
      remoteBranches.addAll(newRemoteBranches)
    }

    val changed = localChanged || remoteChanged
    if (changed) {
      if (showOnlyMy) {
        updateBranchesIsMyState()
      }
      ui.updateBranchesTree(false)
      ui.stopLoadingBranches()
    }
    return changed
  }

  private fun updateBranchesIsFavoriteState() {
    var changed = false
    with(project.service<GitBranchManager>()) {
      for (localBranch in localBranches) {
        val isFavorite = localBranch.repositories.any { isFavorite(GitBranchType.LOCAL, it, localBranch.branchName) }
        changed = changed or (localBranch.isFavorite != isFavorite)
        localBranch.apply { this.isFavorite = isFavorite }
      }
      for (remoteBranch in remoteBranches) {
        val isFavorite = remoteBranch.repositories.any { isFavorite(GitBranchType.REMOTE, it, remoteBranch.branchName) }
        changed = changed or (remoteBranch.isFavorite != isFavorite)
        remoteBranch.apply { this.isFavorite = isFavorite }
      }
    }
    if (changed) {
      ui.refreshTree()
    }
  }

  private fun updateBranchesIsMyState() {
    VcsProjectLog.runWhenLogIsReady(project) { log, _ ->
      val allBranches = localBranches + remoteBranches
      val branchesToCheck = allBranches.filter { it.isMy == ThreeState.UNSURE }
      ui.startLoadingBranches()
      calculateMyBranchesInBackground(
        run = { indicator -> BranchesDashboardUtil.checkIsMyBranchesSynchronously(log, branchesToCheck, indicator) },
        onSuccess = { branches ->
          localBranches.updateUnsureBranchesStateFrom(branches)
          remoteBranches.updateUnsureBranchesStateFrom(branches)
          ui.refreshTree()
        },
        onFinished = {
          ui.stopLoadingBranches()
        })
    }
  }

  private fun Collection<BranchInfo>.updateUnsureBranchesStateFrom(updateFromBranches: Collection<BranchInfo>) {
    if (updateFromBranches.isEmpty()) return

    for (branch in this) {
      if (branch.isMy == ThreeState.UNSURE) {
        branch.isMy = if (updateFromBranches.contains(branch)) ThreeState.YES else ThreeState.NO
      }
    }
  }

  private fun calculateMyBranchesInBackground(run: (ProgressIndicator) -> Set<BranchInfo>,
                                              onSuccess: (Set<BranchInfo>) -> Unit,
                                              onFinished: () -> Unit) {
    var calculatedBranches: Set<BranchInfo>? = null
    object : Task.Backgroundable(project, "Calculating My Branches", true) {
      override fun run(indicator: ProgressIndicator) {
        calculatedBranches = run(indicator)
      }

      override fun onSuccess() {
        val branches = calculatedBranches
        if (branches != null) {
          onSuccess(branches)
        }
      }

      override fun onFinished() {
        onFinished()
      }
    }.queue()
  }

  fun registerDataPackListener(vcsLogData: VcsLogData) {
    vcsLogData.addDataPackChangeListener(changeListener)
  }

  fun removeDataPackListener(vcsLogData: VcsLogData) {
    vcsLogData.removeDataPackChangeListener(changeListener)
  }
}