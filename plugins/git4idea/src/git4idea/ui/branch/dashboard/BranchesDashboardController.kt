// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ui.branch.dashboard

import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.ThreeState
import com.intellij.vcs.log.data.DataPackChangeListener
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.impl.VcsProjectLog
import kotlin.properties.Delegates

internal class BranchesDashboardController(private val project: Project,
                                           private val ui: BranchesDashboardUi) : Disposable {

  private val changeListener = DataPackChangeListener { ui.updateBranchesTree(false) }

  val localBranches = hashSetOf<BranchInfo>()
  val remoteBranches = hashSetOf<BranchInfo>()
  var showOnlyMy: Boolean by Delegates.observable(false) { _, old, new -> if (old != new) updateBranchesIsMyState() }

  init {
    Disposer.register(ui, this)
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