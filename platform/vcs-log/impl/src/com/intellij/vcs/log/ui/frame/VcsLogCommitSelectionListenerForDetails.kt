// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.frame

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.util.Disposer
import com.intellij.util.containers.MultiMap
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.VcsRef
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.ui.details.CommitDetailsListPanel
import com.intellij.vcs.log.ui.details.commit.CommitDetailsPanel
import com.intellij.vcs.log.ui.details.commit.CommitDetailsPanel.RootColor
import com.intellij.vcs.log.ui.frame.CommitPresentationUtil.CommitPresentation
import com.intellij.vcs.log.ui.table.CommitSelectionListener
import com.intellij.vcs.log.ui.table.VcsLogGraphTable
import com.intellij.vcs.log.util.VcsLogUtil
import org.jetbrains.annotations.Nls

class VcsLogCommitSelectionListenerForDetails private constructor(graphTable: VcsLogGraphTable,
                                                                  private val detailsPanel: CommitDetailsListPanel,
                                                                  parentDisposable: Disposable)
  : CommitSelectionListener<VcsCommitMetadata>(graphTable, graphTable.logData.miniDetailsGetter), Disposable {

  private val logData = graphTable.logData
  private val containingBranchesGetter = logData.containingBranchesGetter
  private val colorManager = graphTable.colorManager

  private val refsLoader = CommitDataLoader()
  private val hashesResolver = CommitDataLoader()

  private var selection = emptyList<Int>()

  init {
    val containingBranchesListener = Runnable { branchesChanged() }
    containingBranchesGetter.addTaskCompletedListener(containingBranchesListener)

    Disposer.register(this) { containingBranchesGetter.removeTaskCompletedListener(containingBranchesListener) }
    Disposer.register(parentDisposable, this)
  }

  override fun onSelection(selection: IntArray) {
    cancelLoading()

    val commits = myGraphTable.model.getCommitIds(selection)
    val displayedCommits = detailsPanel.rebuildPanel(commits)

    val currentSelection = selection.copyOf(displayedCommits.size).toList()
    this.selection = currentSelection

    detailsPanel.forEachPanel { commit, panel ->
      panel.setBranches(containingBranchesGetter.requestContainingBranches(commit.root, commit.hash))
      val root = commit.root
      if (colorManager.hasMultiplePaths()) {
        panel.setRoot(RootColor(root, VcsLogGraphTable.getRootBackgroundColor(root, colorManager)))
      }
      else {
        panel.setRoot(null)
      }
    }

    refsLoader.loadData(
      { currentSelection.map(myGraphTable.model::getRefsAtRow) },
      { panel, refs -> panel.setRefs(sortRefs(refs)) })
  }

  private fun sortRefs(refs: Collection<VcsRef>): List<VcsRef> {
    val root = refs.firstOrNull()?.root ?: return emptyList()
    return refs.sortedWith(logData.getLogProvider(root).referenceManager.labelsOrderComparator)
  }

  override fun onDetailsLoaded(detailsList: List<VcsCommitMetadata>) {
    val unResolvedHashes = mutableSetOf<String>()
    val presentations = detailsList.map {
      CommitPresentationUtil.buildPresentation(logData.project, it, unResolvedHashes)
    }
    detailsPanel.forEachPanelIndexed { i: Int, panel: CommitDetailsPanel ->
      panel.setCommit(presentations[i])
    }
    if (unResolvedHashes.isNotEmpty()) {
      hashesResolver.loadData(
        { doResolveHashes(presentations, unResolvedHashes) },
        { panel, presentation -> panel.setCommit(presentation) })
    }
  }

  override fun onEmptySelection() {
    cancelLoading()
    setEmpty(VcsLogBundle.message("vcs.log.changes.details.no.commits.selected.status"))
  }

  override fun getSelectionToLoad() = selection

  override fun onLoadingStarted() = detailsPanel.startLoadingDetails()

  override fun onLoadingStopped() = detailsPanel.stopLoadingDetails()

  override fun onError(error: Throwable) {
    setEmpty(VcsLogBundle.message("vcs.log.error.loading.status"))
  }

  private fun setEmpty(text: @Nls String) {
    detailsPanel.setStatusText(text)
    selection = emptyList()
    detailsPanel.rebuildPanel(emptyList())
  }

  private fun doResolveHashes(presentations: List<CommitPresentation>,
                              unResolvedHashes: MutableSet<String>): List<CommitPresentation> {
    val resolvedHashes = MultiMap<String, CommitId>()

    val fullHashes = unResolvedHashes.filter { it.length == VcsLogUtil.FULL_HASH_LENGTH }

    for (fullHash in fullHashes) {
      val hash = HashImpl.build(fullHash)
      for (root in logData.roots) {
        val id = CommitId(hash, root)
        if (logData.storage.containsCommit(id)) {
          resolvedHashes.putValue(fullHash, id)
        }
      }
      unResolvedHashes.remove(fullHash)
    }

    if (unResolvedHashes.isNotEmpty()) {
      logData.storage.iterateCommits { commitId: CommitId ->
        for (hashString in unResolvedHashes) {
          if (commitId.hash.asString().startsWith(hashString, true)) {
            resolvedHashes.putValue(hashString, commitId)
          }
        }
        true
      }
    }

    return presentations.map {
      it.resolve(resolvedHashes)
    }
  }

  private fun branchesChanged() {
    detailsPanel.forEachPanel { commit, panel ->
      panel.setBranches(containingBranchesGetter.requestContainingBranches(commit.root, commit.hash))
    }
  }

  private fun cancelLoading() {
    hashesResolver.cancelLoading()
    refsLoader.cancelLoading()
  }

  override fun dispose() {
    cancelLoading()
  }

  private inner class CommitDataLoader {
    private var progressIndicator: ProgressIndicator? = null

    fun <T> loadData(loadData: (ProgressIndicator) -> List<T>, setData: (CommitDetailsPanel, T) -> Unit) {
      val currentSelection = selection
      progressIndicator = BackgroundTaskUtil.executeOnPooledThread(this@VcsLogCommitSelectionListenerForDetails) {
        val indicator = ProgressManager.getInstance().progressIndicator
        val loaded = loadData(indicator)
        ApplicationManager.getApplication()
          .invokeLater({
            progressIndicator = null
            detailsPanel.forEachPanelIndexed { i: Int, panel: CommitDetailsPanel ->
              setData(panel, loaded[i])
            }
          },
            { progressIndicator != indicator || currentSelection != selection })
      }
    }

    fun cancelLoading() {
      if (progressIndicator != null) {
        progressIndicator!!.cancel()
        progressIndicator = null
      }
    }
  }

  companion object {
    @JvmStatic
    fun install(graphTable: VcsLogGraphTable,
                detailsPanel: CommitDetailsListPanel,
                disposable: Disposable) {
      val listener = VcsLogCommitSelectionListenerForDetails(graphTable, detailsPanel, disposable)
      graphTable.selectionModel.addListSelectionListener(listener)
    }
  }
}