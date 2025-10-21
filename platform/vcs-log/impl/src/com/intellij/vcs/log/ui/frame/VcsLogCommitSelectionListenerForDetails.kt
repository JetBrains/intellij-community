// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.frame

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.containers.MultiMap
import com.intellij.vcs.log.*
import com.intellij.vcs.log.data.*
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.ui.VcsLogColorManager
import com.intellij.vcs.log.ui.details.CommitDetailsListPanel
import com.intellij.vcs.log.ui.details.commit.CommitDetailsPanel
import com.intellij.vcs.log.ui.details.commit.CommitDetailsPanel.RootColor
import com.intellij.vcs.log.ui.frame.CommitPresentationUtil.CommitPresentation
import com.intellij.vcs.log.util.VcsLogUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
class VcsLogCommitSelectionListenerForDetails(
  private val logData: VcsLogData,
  private val colorManager: VcsLogColorManager,
  private val detailsPanel: CommitDetailsListPanel,
  parentDisposable: Disposable,
) : CommitDetailsLoader.Listener<VcsCommitMetadata>, Disposable {

  private val hashesResolver = CommitDataLoader()
  private val containingBranchesLoader = ContainingBranchesAsyncLoader(logData.containingBranchesGetter, detailsPanel).also {
    Disposer.register(this, it)
  }
  private val externalStatusesLoader = ExternalStatusesAsyncLoader(logData.project, detailsPanel).also {
    Disposer.register(this, it)
  }

  init {
    Disposer.register(parentDisposable, this)
  }

  override fun onSelection() {
    cancelLoading()
  }

  override fun onDetailsLoaded(hashedCommitsIds: List<VcsLogCommitStorageIndex>, details: List<VcsCommitMetadata>) {
    val detailsList = details.take(MAX_COMMITS_TO_LOAD)
    //TODO: replace with detailsPanel.setCommits
    val commitIds = detailsList.map { CommitId(it.id, it.root) }
    detailsPanel.rebuildPanel(commitIds)
    detailsPanel.showOverflowLabelIfNeeded(MAX_COMMITS_TO_LOAD, hashedCommitsIds.size)

    val unResolvedHashes = mutableSetOf<String>()
    val presentations = detailsList.map { CommitPresentationUtil.buildPresentation(logData.project, it, unResolvedHashes) }
    val refsModel = logData.dataPack.refsModel
    detailsPanel.forEachPanelIndexed { idx, panel ->
      val presentation = presentations[idx]
      panel.setCommit(presentation)

      val root = commitIds[idx].root
      if (colorManager.hasMultiplePaths()) {
        panel.setRoot(RootColor(root, colorManager.getRootColor(root)))
      }
      else {
        panel.setRoot(null)
      }

      val id = hashedCommitsIds[idx]
      val refs = refsModel.refsToCommit(root, id)
      panel.setRefs(sortRefs(refs))
    }

    containingBranchesLoader.requestData(commitIds)
    externalStatusesLoader.requestData(commitIds)

    if (unResolvedHashes.isNotEmpty()) {
      hashesResolver.loadData(
        { doResolveHashes(presentations, unResolvedHashes) },
        { panel, presentation -> panel.setCommit(presentation) })
    }
  }

  private fun sortRefs(refs: Collection<VcsRef>): List<VcsRef> {
    val root = refs.firstOrNull()?.root ?: return emptyList()
    return refs.sortedWith(logData.getLogProvider(root).referenceManager.labelsOrderComparator)
  }

  override fun onEmptySelection() {
    cancelLoading()
    setEmpty(VcsLogBundle.message("vcs.log.changes.details.no.commits.selected.status"))
  }

  override fun onLoadingStarted() = detailsPanel.startLoadingDetails()

  override fun onLoadingStopped() = detailsPanel.stopLoadingDetails()

  override fun onError(error: Throwable) {
    setEmpty(VcsLogBundle.message("vcs.log.error.loading.details.status"))
  }

  private fun setEmpty(text: @Nls String) {
    detailsPanel.setStatusText(text)
    detailsPanel.rebuildPanel(emptyList())
    detailsPanel.showOverflowLabelIfNeeded(MAX_COMMITS_TO_LOAD, 0)
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

  private fun cancelLoading() {
    hashesResolver.cancelLoading()
    containingBranchesLoader.requestData(emptyList())
    externalStatusesLoader.requestData(emptyList())
  }

  override fun dispose() {
    cancelLoading()
  }

  private inner class CommitDataLoader {
    private var progressIndicator: ProgressIndicator? = null

    fun <T> loadData(loadData: (ProgressIndicator) -> List<T>, setData: (CommitDetailsPanel, T) -> Unit) {
      progressIndicator = BackgroundTaskUtil.executeOnPooledThread(this@VcsLogCommitSelectionListenerForDetails) {
        val indicator = ProgressManager.getInstance().progressIndicator
        val loaded = loadData(indicator)
        ApplicationManager.getApplication()
          .invokeLater({
                         progressIndicator = null
                         detailsPanel.forEachPanelIndexed { i: Int, panel: CommitDetailsPanel ->
                           setData(panel, loaded[i])
                         }
                       }, { indicator.isCanceled })
      }
    }

    fun cancelLoading() {
      if (progressIndicator != null) {
        progressIndicator!!.cancel()
        progressIndicator = null
      }
    }
  }

  private class ContainingBranchesAsyncLoader(private val getter: ContainingBranchesGetter,
                                              private val detailsPanel: CommitDetailsListPanel) : Disposable {

    private var requestedCommits: List<CommitId> = emptyList()

    init {
      val containingBranchesListener = Runnable { branchesChanged() }
      getter.addTaskCompletedListener(containingBranchesListener)
      Disposer.register(this) { getter.removeTaskCompletedListener(containingBranchesListener) }
    }

    private fun branchesChanged() {
      requestData(requestedCommits, fromCache = true)
    }

    fun requestData(commits: List<CommitId>, fromCache: Boolean = false) {
      val result = mutableMapOf<CommitId, List<String>>()
      for (commit in commits) {
        val branches = if (fromCache) {
          getter.getContainingBranchesFromCache(commit.root, commit.hash)
        }
        else {
          getter.requestContainingBranches(commit.root, commit.hash)
        }
        if (branches != null) result[commit] = branches
      }

      if (result.isNotEmpty()) {
        detailsPanel.forEachPanel { commit, panel ->
          panel.setBranches(result[commit])
        }
      }
      requestedCommits = commits
    }

    override fun dispose() {}
  }

  private class ExternalStatusesAsyncLoader(private val project: Project,
                                            private val detailsPanel: CommitDetailsListPanel)
    : Disposable {

    private var loadersDisposable: Disposable? = null
    private var loaders: List<ProviderLoader<*>>? = null

    private var statuses = mutableMapOf<CommitId, MutableMap<String, VcsCommitExternalStatusPresentation>>()

    init {
      VcsCommitExternalStatusProvider.addProviderListChangeListener(this) {
        loadersDisposable?.let { Disposer.dispose(it) }
        loaders = null
        requestData(statuses.keys.toList())
      }
    }

    fun requestData(commits: List<CommitId>) {
      statuses = mutableMapOf()
      if (commits.isEmpty()) {
        loaders?.forEach {
          it.requestData(emptyList()) {}
        }
        return
      }

      getLoaders().forEach { loader ->
        loader.requestData(commits) { result ->

          for ((commit, statusPresentation) in result) {
            val presentations = statuses.getOrPut(commit) {
              mutableMapOf()
            }
            if (statusPresentation == null) {
              presentations.remove(loader.id)
            }
            else {
              presentations[loader.id] = statusPresentation
            }
          }

          detailsPanel.forEachPanel { commit, panel ->
            panel.setStatuses(statuses[commit]?.values?.toList().orEmpty())
          }
        }
      }
    }

    private fun getLoaders(): List<ProviderLoader<*>> {
      if (loaders == null && !Disposer.isDisposed(this)) {
        val disposable = Disposer.newDisposable(this, "Status loaders")
        loaders = VcsCommitExternalStatusProvider.EP.extensions.map { provider ->
          ProviderLoader(project, provider).also {
            Disposer.register(disposable, it)
          }
        }
        loadersDisposable = disposable
      }
      return loaders!!
    }


    override fun dispose() {}

    private class ProviderLoader<T : VcsCommitExternalStatus>(private val project: Project,
                                                              private val provider: VcsCommitExternalStatusProvider<T>)
      : Disposable {

      val id = provider.id
      private val loader = provider.createLoader(project).also {
        Disposer.register(this, it)
      }

      fun requestData(commits: List<CommitId>, onChange: (Map<CommitId, VcsCommitExternalStatusPresentation?>) -> Unit) {
        loader.loadData(commits) {
          val presentations = it.mapValues { (_, status) -> provider.getPresentation(project, status) }
          onChange(presentations)
        }
      }

      override fun dispose() {}
    }
  }

  companion object {
    internal const val MAX_COMMITS_TO_LOAD = 50
  }
}