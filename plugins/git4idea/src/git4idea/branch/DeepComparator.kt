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
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.platform.vcs.impl.shared.telemetry.VcsScope
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcs.log.*
import com.intellij.vcs.log.data.DataPack
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.ui.VcsLogUiEx
import com.intellij.vcs.log.ui.highlighters.MergeCommitsHighlighter
import com.intellij.vcs.log.ui.highlighters.VcsLogHighlighterFactory
import com.intellij.vcs.log.util.VcsLogUiUtil
import com.intellij.vcs.log.util.VcsLogUtil
import com.intellij.vcs.log.util.findBranch
import com.intellij.vcs.log.util.subgraphDifference
import com.intellij.vcs.log.visible.VisiblePack
import git4idea.GitNotificationIdsHolder.Companion.COULD_NOT_COMPARE_WITH_BRANCH
import git4idea.GitUtil
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSet
import org.jetbrains.annotations.NonNls
import java.awt.Point

class DeepComparator(private val project: Project,
                     private val repositoryManager: GitRepositoryManager,
                     private val vcsLogData: VcsLogData,
                     private val ui: VcsLogUi,
                     parent: Disposable) : VcsLogHighlighter, Disposable {
  private val storage
    get() = vcsLogData.storage

  private var progressIndicator: ProgressIndicator? = null
  private var comparedBranch: String? = null
  private var repositoriesWithCurrentBranches: Map<GitRepository, String>? = null
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
      val repositories = getRepositories(dataPack.logProviders, comparedBranch!!)
      if (repositories == repositoriesWithCurrentBranches) {
        // but not if current branch changed
        startTask(dataPack)
      }
      else {
        LOG.debug("Repositories with current branches changed. Actual:\n$repositories\nExpected:\n$repositoriesWithCurrentBranches")
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

    val repositories = getRepositories(ui.dataPack.logProviders, branchToCompare)
    if (repositories.isEmpty()) {
      LOG.debug("Could not find suitable repositories for selected branch $comparedBranch")
      return
    }

    comparedBranch = branchToCompare
    repositoriesWithCurrentBranches = repositories
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
    LOG.debug("Highlighting requested for $repositoriesWithCurrentBranches")
    val task = MyTask(repositoriesWithCurrentBranches!!, dataPack, comparedBranch!!)
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
    repositoriesWithCurrentBranches = null
  }

  private fun getRepositories(providers: Map<VirtualFile, VcsLogProvider>,
                              branchToCompare: String): Map<GitRepository, String> {
    return providers.keys.mapNotNull { repositoryManager.getRepositoryForRootQuick(it) }.filter { repository ->
      repository.branches.findBranchByName(branchToCompare) != null
    }.associateWith { it.currentBranch?.name ?: GitUtil.HEAD }
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

  private inner class MyTask(private val repositoriesWithCurrentBranches: Map<GitRepository, String>,
                             vcsLogDataPack: VcsLogDataPack,
                             private val comparedBranch: String) :
    Task.Backgroundable(project, GitBundle.message("git.log.cherry.picked.highlighter.process")) {

    private val dataPack = (vcsLogDataPack as? VisiblePack)?.dataPack as? DataPack
    private val collectedNonPickedCommits = IntOpenHashSet()
    private var exception: VcsException? = null

    override fun run(indicator: ProgressIndicator) {
      try {
        repositoriesWithCurrentBranches.forEach { (repo, currentBranch) ->
          val commits = if (Registry.`is`("git.log.use.index.for.picked.commits.highlighting")) {
            if (Registry.`is`("git.log.fast.picked.commits.highlighting")) {
              getCommitsByIndexFast(repo.root, comparedBranch, currentBranch) ?: getCommitsByIndexReliable(repo.root, comparedBranch, currentBranch)
            }
            else {
              getCommitsByIndexReliable(repo.root, comparedBranch, currentBranch)
            }
          }
          else {
            getCommitsByPatch(repo.root, comparedBranch, currentBranch)
          }
          collectedNonPickedCommits.addAll(commits)
        }
      }
      catch (e: VcsException) {
        LOG.warn(e)
        exception = e
      }
    }

    override fun onFinished() {
      progressIndicator = null
    }

    override fun onSuccess() {
      if (exception != null) {
        nonPickedCommits = null
        VcsNotifier.getInstance(project).notifyError(COULD_NOT_COMPARE_WITH_BRANCH,
                                                     GitBundle.message("git.log.cherry.picked.highlighter.error.message", comparedBranch),
                                                     exception!!.message)
        return
      }
      nonPickedCommits = collectedNonPickedCommits
    }

    @Throws(VcsException::class)
    private fun getCommitsByPatch(root: VirtualFile,
                                  targetBranch: String,
                                  sourceBranch: String): IntSet {
      return recordSpan(root, "Getting non picked commits with git") {
        getCommitsFromGit(root, targetBranch, sourceBranch)
      }
    }

    @Throws(VcsException::class)
    private fun getCommitsByIndexReliable(root: VirtualFile, sourceBranch: String, targetBranch: String): IntSet {
      val resultFromGit = getCommitsByPatch(root, targetBranch, sourceBranch)
      if (dataPack == null || !dataPack.isFull) return resultFromGit

      val resultFromIndex = recordSpan(root, "Getting non picked commits with index reliable") {
        val sourceBranchRef = dataPack.refsModel.findBranch(sourceBranch, root) ?: return resultFromGit
        val targetBranchRef = dataPack.refsModel.findBranch(targetBranch, root) ?: return resultFromGit
        getCommitsFromIndex(dataPack, root, sourceBranchRef, targetBranchRef, resultFromGit, true)
      }

      return resultFromIndex ?: resultFromGit
    }

    private fun getCommitsByIndexFast(root: VirtualFile, sourceBranch: String, targetBranch: String): IntSet? {
      if (!vcsLogData.index.isIndexed(root) || dataPack == null || !dataPack.isFull) return null

      return recordSpan(root, "Getting non picked commits with index fast") {
        val sourceBranchRef = dataPack.refsModel.findBranch(sourceBranch, root) ?: return null
        val targetBranchRef = dataPack.refsModel.findBranch(targetBranch, root) ?: return null
        val sourceBranchCommits = dataPack.subgraphDifference(sourceBranchRef, targetBranchRef, storage) ?: return null
        getCommitsFromIndex(dataPack, root, sourceBranchRef, targetBranchRef, sourceBranchCommits, false)
      }
    }

    @Throws(VcsException::class)
    private fun getCommitsFromGit(root: VirtualFile,
                                  currentBranch: String,
                                  comparedBranch: String): IntSet {
      val handler = GitLineHandler(project, root, GitCommand.CHERRY)
      handler.addParameters(currentBranch, comparedBranch) // upstream - current branch; head - compared branch

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

    private fun getCommitsFromIndex(dataPack: DataPack?, root: VirtualFile,
                                    sourceBranchRef: VcsRef, targetBranchRef: VcsRef,
                                    sourceBranchCommits: IntSet, reliable: Boolean): IntSet? {
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
                  " that were picked to the current branch" +
                  (if (reliable) " with different patch id but matching cherry-picked suffix"
                  else " with matching author, author time and message"))
      }

      return sourceBranchCommits
    }

    override fun toString(): String {
      return "Task for '$comparedBranch' in $repositoriesWithCurrentBranches" //NON-NLS
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
      return GitBundle.message("action.Git.Log.DeepCompare.text")
    }

    override fun showMenuItem(): Boolean {
      return false
    }
  }

  companion object {
    private val LOG = Logger.getInstance(DeepComparator::class.java)

    @JvmStatic
    fun getInstance(project: Project, dataProvider: VcsLogData, logUi: VcsLogUi): DeepComparator {
      return project.getService(DeepComparatorHolder::class.java).getInstance(dataProvider, logUi)
    }

    @JvmStatic
    fun getComparedBranchFromFilters(filters: VcsLogFilterCollection, refs: VcsLogRefs): String? {
      val singleFilteredBranch = VcsLogUtil.getSingleFilteredBranch(filters, refs)
      if (singleFilteredBranch != null) return singleFilteredBranch

      val rangeFilter = filters.get(VcsLogFilterCollection.RANGE_FILTER) ?: return null
      return rangeFilter.ranges.singleOrNull()?.inclusiveRef
    }
  }

  private inline fun <R> recordSpan(root: VirtualFile, @NonNls actionName: String, block: () -> R): R {
    return TelemetryManager.getInstance().getTracer(VcsScope).spanBuilder(actionName).use { span ->
      span.setAttribute("rootName", root.name)
      block()
    }
  }
}