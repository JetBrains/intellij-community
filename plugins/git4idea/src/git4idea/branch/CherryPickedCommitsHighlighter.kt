// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.branch

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcs.log.VcsCommitStyleFactory
import com.intellij.vcs.log.VcsLogAggregatedStoredRefs
import com.intellij.vcs.log.VcsLogDataPack
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.VcsLogHighlighter
import com.intellij.vcs.log.VcsLogUi
import com.intellij.vcs.log.VcsShortCommitDetails
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.data.VcsLogGraphData
import com.intellij.vcs.log.ui.VcsLogUiEx
import com.intellij.vcs.log.ui.highlighters.MergeCommitsHighlighter
import com.intellij.vcs.log.ui.highlighters.VcsLogHighlighterFactory
import com.intellij.vcs.log.util.VcsLogUiUtil
import com.intellij.vcs.log.util.VcsLogUtil
import com.intellij.vcs.log.visible.VisiblePack
import git4idea.GitNotificationIdsHolder.Companion.COULD_NOT_COMPARE_WITH_BRANCH
import git4idea.GitUtil
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import java.awt.Point

internal class CherryPickedCommitsHighlighter(
  private val project: Project,
  private val repositoryManager: GitRepositoryManager,
  private val vcsLogData: VcsLogData,
  private val ui: VcsLogUi,
  parent: Disposable,
) : VcsLogHighlighter, Disposable {
  private var progressIndicator: ProgressIndicator? = null
  private var comparedBranch: String? = null
  private var repositoriesWithTargetBranches: Map<GitRepository, String>? = null
  private var nonPickedCommits: IntOpenHashSet? = null

  init {
    Disposer.register(parent, this)
  }

  override fun getStyle(commitId: Int,
                        commitDetails: VcsShortCommitDetails,
                        column: Int,
                        isSelected: Boolean): VcsLogHighlighter.VcsCommitStyle {
    if (nonPickedCommits == null || nonPickedCommits!!.contains(commitId)) return VcsLogHighlighter.VcsCommitStyle.DEFAULT
    else return VcsCommitStyleFactory.foreground(MergeCommitsHighlighter.MERGE_COMMIT_FOREGROUND)
  }

  override fun update(dataPack: VcsLogDataPack, refreshHappened: Boolean) {
    if (comparedBranch == null) { // no branch is selected => not interested in refresh events
      return
    }

    val singleFilteredBranch = getComparedBranchFromFilters(dataPack.filters, dataPack.refs)
    if (comparedBranch != singleFilteredBranch) {
      val oldComparedBranch = comparedBranch
      LOG.debug("Branch filter changed. Compared branch: $oldComparedBranch, filtered branch: $singleFilteredBranch")
      stopTaskAndUnhighlight()
      notifyUnhighlight(oldComparedBranch)
      return
    }

    if (refreshHappened) {
      stopTask()

      // highlight again
      val repositories = getRepositoriesWithTargetBranches(dataPack, comparedBranch!!)
      if (repositories == repositoriesWithTargetBranches) {
        // but not if the target branch changed
        startTask(dataPack)
      }
      else {
        LOG.debug("Repositories with target branches changed. Actual:\n$repositories\nExpected:\n$repositoriesWithTargetBranches")
        unhighlight()
      }
    }
  }

  @RequiresEdt
  fun startTask(dataPack: VcsLogDataPack, branchToCompare: String) {
    ThreadingAssertions.assertEventDispatchThread()
    if (comparedBranch != null) {
      LOG.error("Already comparing with branch $comparedBranch")
      return
    }

    val repositories = getRepositoriesWithTargetBranches(ui.dataPack, branchToCompare)
    if (repositories.isEmpty()) {
      LOG.debug("Could not find suitable repositories for selected branch $comparedBranch")
      return
    }

    comparedBranch = branchToCompare
    repositoriesWithTargetBranches = repositories
    startTask(dataPack)
  }

  @RequiresEdt
  fun stopTaskAndUnhighlight() {
    ThreadingAssertions.assertEventDispatchThread()
    stopTask()
    unhighlight()
  }

  @RequiresEdt
  fun hasHighlightingOrInProgress(): Boolean {
    ThreadingAssertions.assertEventDispatchThread()
    return comparedBranch != null
  }

  private fun startTask(dataPack: VcsLogDataPack) {
    LOG.debug("Highlighting requested for $repositoriesWithTargetBranches")
    val task = MyTask(repositoriesWithTargetBranches!!, dataPack, comparedBranch!!)
    progressIndicator = BackgroundableProcessIndicator(task)
    ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, progressIndicator!!)
  }

  private fun stopTask() {
    if (progressIndicator != null) {
      progressIndicator!!.cancel()
      progressIndicator = null
    }
  }

  private fun unhighlight() {
    nonPickedCommits = null
    comparedBranch = null
    repositoriesWithTargetBranches = null
  }

  private fun getRepositoriesWithTargetBranches(dataPack: VcsLogDataPack,
                                                branchToCompare: String): Map<GitRepository, String> {
    val providers = dataPack.logProviders
    val isCompareBranchesUi = ui is GitCompareBranchesUi.MyVcsLogUi
    val targetBranchFromRangeFilter = if (isCompareBranchesUi) getTargetBranchFromRangeFilter(dataPack.filters) else null

    return providers.keys.mapNotNull { repositoryManager.getRepositoryForRootQuick(it) }.filter { repository ->
      repository.branches.findBranchByName(branchToCompare) != null
    }.associateWith { targetBranchFromRangeFilter ?: it.currentBranch?.name ?: GitUtil.HEAD }
  }

  private fun notifyUnhighlight(branch: String?) {
    if (ui is VcsLogUiEx) {
      val message = GitBundle.message("git.log.cherry.picked.highlighter.cancelled.message", branch)
      val balloon = JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(message, null,
                                                                              MessageType.INFO.popupBackground, null)
        .setFadeoutTime(5000)
        .createBalloon()
      val component = VcsLogUiUtil.getComponent(ui)
      balloon.show(RelativePoint(component, Point(component.width / 2, component.visibleRect.y)), Balloon.Position.below)
      Disposer.register(this, balloon)
    }
  }

  override fun dispose() {
    stopTaskAndUnhighlight()
  }

  private inner class MyTask(private val repositoriesWithTargetBranches: Map<GitRepository, String>,
                             vcsLogDataPack: VcsLogDataPack,
                             private val comparedBranch: String) :
    Task.Backgroundable(project, GitBundle.message("git.log.cherry.picked.highlighter.process")) {

    private val comparator =
      DeepComparator(project, vcsLogData, (vcsLogDataPack as? VisiblePack)?.dataPack as? VcsLogGraphData,
                     repositoriesWithTargetBranches, comparedBranch)

    override fun run(indicator: ProgressIndicator) {
      comparator.compare()
    }

    override fun onFinished() {
      progressIndicator = null
    }

    override fun onSuccess() {
      val exception = comparator.exception
      if (exception != null) {
        nonPickedCommits = null
        VcsNotifier.getInstance(project).notifyError(COULD_NOT_COMPARE_WITH_BRANCH,
                                                     GitBundle.message("git.log.cherry.picked.highlighter.error.message", comparedBranch),
                                                     exception.message)
        return
      }
      nonPickedCommits = comparator.collectedNonPickedCommits
    }

    override fun toString(): String {
      return "Task for '$comparedBranch' in $repositoriesWithTargetBranches" //NON-NLS
    }
  }

  class Factory : VcsLogHighlighterFactory {

    override fun createHighlighter(logData: VcsLogData, logUi: VcsLogUi): VcsLogHighlighter {
      return getInstance(logData.project, logData, logUi)
    }

    override fun getId(): String {
      return "CHERRY_PICKED_COMMITS" //NON-NLS
    }

    override fun getTitle(): String {
      // this method is not used as there is a separate action and showMenuItem returns false
      return GitBundle.message("action.Git.Log.HighlightCherryPickedCommits.text")
    }

    override fun showMenuItem(): Boolean {
      return false
    }
  }

  companion object {
    private val LOG = Logger.getInstance(CherryPickedCommitsHighlighter::class.java)

    @JvmStatic
    fun getInstance(project: Project, dataProvider: VcsLogData, logUi: VcsLogUi): CherryPickedCommitsHighlighter {
      return project.getService(CherryPickedCommitsHighlighterHolder::class.java).getInstance(dataProvider, logUi)
    }

    @JvmStatic
    fun getComparedBranchFromFilters(filters: VcsLogFilterCollection, refs: VcsLogAggregatedStoredRefs): String? {
      val singleFilteredBranch = VcsLogUtil.getSingleFilteredBranch(filters, refs)
      if (singleFilteredBranch != null) return singleFilteredBranch

      val rangeFilter = filters.get(VcsLogFilterCollection.RANGE_FILTER) ?: return null
      return rangeFilter.ranges.singleOrNull()?.inclusiveRef
    }

    @JvmStatic
    private fun getTargetBranchFromRangeFilter(filters: VcsLogFilterCollection): String? {
      val rangeFilter = filters.get(VcsLogFilterCollection.RANGE_FILTER) ?: return null
      return rangeFilter.ranges.singleOrNull()?.exclusiveRef
    }
  }
}
