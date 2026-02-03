// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.changes.viewModel

import com.intellij.codeWithMe.ClientId
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor
import com.intellij.openapi.vcs.changes.ChangesViewDiffAction
import com.intellij.openapi.vcs.changes.ChangesViewI
import com.intellij.openapi.vcs.changes.ChangesViewId
import com.intellij.openapi.vcs.changes.ChangesViewManager
import com.intellij.openapi.vcs.changes.ChangesViewSplitComponentBinding
import com.intellij.openapi.vcs.changes.CommitChangesViewWithToolbarPanel
import com.intellij.openapi.vcs.changes.InclusionModel
import com.intellij.openapi.vcs.changes.RemoteChangesViewDiffPreviewProcessor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.kernel.ids.BackendValueIdType
import com.intellij.platform.kernel.ids.storeValueGlobally
import com.intellij.platform.vcs.impl.shared.changes.ChangesTreePath
import com.intellij.platform.vcs.impl.shared.rpc.BackendChangesViewEvent
import com.intellij.platform.vcs.impl.shared.rpc.ChangesViewApi
import com.intellij.platform.vcs.impl.shared.rpc.ChangesViewDiffableSelection
import com.intellij.ui.split.createComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JComponent
import kotlin.time.Duration.Companion.minutes

/**
 * Events-based implementation of [CommitChangesViewWithToolbarPanel].
 * Events are emitted via [eventsForFrontend] and should be propagated to the frontend via RPC ([ChangesViewApi]).
 *
 * Can be used both in monolith and split modes.
 *
 * @see [com.intellij.platform.vcs.impl.shared.rpc.ChangesViewApi.getBackendChangesViewEvents]
 */
internal class RpcChangesViewProxy(project: Project, scope: CoroutineScope) : ChangesViewProxy(project, scope) {
  private val _eventsForFrontend =
    MutableSharedFlow<BackendChangesViewEvent>(extraBufferCapacity = DEFAULT_BUFFER_SIZE, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private val _modelRefreshes = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private val refresher = BackendRemoteCommitChangesViewModelRefresher(scope, _eventsForFrontend)

  val eventsForFrontend: SharedFlow<BackendChangesViewEvent> = _eventsForFrontend.asSharedFlow()

  /**
   * Emits value every time a model refresh is performed.
   */
  val modelRefreshes: SharedFlow<Unit> = _modelRefreshes.asSharedFlow()

  val inclusionModel = MutableStateFlow<InclusionModel?>(null)

  val diffableSelection = MutableStateFlow<ChangesViewDiffableSelection?>(null)

  private var _panel: JComponent? = null
  override val panel: JComponent
    get() = _panel ?: error("Panel is not initialized yet")

  override val inclusionChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  override val diffRequests = MutableSharedFlow<Pair<ChangesViewDiffAction, ClientId>>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  override fun setInclusionModel(model: InclusionModel?) {
    inclusionModel.value = model
  }

  override fun initPanel() {
    val id = storeValueGlobally(scope, Unit, BackendChangesViewValueIdType)
    _panel = ChangesViewSplitComponentBinding.createComponent(project, scope, id)
  }

  override fun setToolbarHorizontal(horizontal: Boolean) {
  }

  override fun scheduleRefreshNow(callback: Runnable?) {
    refresher.scheduleRefreshNow(callback)
  }

  override fun scheduleDelayedRefresh() {
    refresher.scheduleDelayedRefresh()
  }

  override fun setGrouping(groupingKey: String) {
  }

  override fun resetViewImmediatelyAndRefreshLater() {
  }

  override fun getDisplayedChanges(): List<Change> = emptyList()

  override fun getIncludedChanges(): List<Change> = getIncluded<Change>()

  override fun getDisplayedUnversionedFiles(): List<FilePath> = emptyList()

  override fun getIncludedUnversionedFiles(): List<FilePath> = getIncluded<FilePath>()

  private inline fun <reified T> getIncluded(): List<T> = inclusionModel.value?.getInclusion()?.filterIsInstance<T>().orEmpty()

  override fun expand(item: Any) {
  }

  override fun select(item: Any) {
    val treePath = ChangesTreePath.create(item)
    if (treePath == null) {
      LOG.warn("Cannot find tree path for $item")
      return
    }

    selectPath(treePath)
  }

  override fun selectFirst(items: Collection<Any>) {
  }

  override fun selectFile(vFile: VirtualFile?) {
  }

  override fun selectChanges(changes: List<Change>) {
  }

  fun selectPath(path: ChangesTreePath) {
    _eventsForFrontend.tryEmit(BackendChangesViewEvent.SelectPath(path))
  }

  // Diff request producers are required only for showing external diff, so can be skipped in Split mode
  override fun getDiffRequestProducers(selectedOnly: Boolean) = null

  override fun hasContentToDiff(): Boolean = diffableSelection.value != null

  override fun createDiffPreviewProcessor(isInEditor: Boolean): ChangeViewDiffRequestProcessor =
    RemoteChangesViewDiffPreviewProcessor(this, isInEditor)

  fun inclusionChanged() {
    inclusionChanged.tryEmit(Unit)
  }

  fun refreshPerformed(counter: Int) {
    refresher.refreshPerformed(counter)
    _modelRefreshes.tryEmit(Unit)
  }

  fun selectionUpdated(selection: ChangesViewDiffableSelection?) {
    diffableSelection.value = selection
  }

  companion object {
    private val LOG = logger<RpcChangesViewProxy>()
  }
}

private val REFRESH_TIMEOUT = 1.minutes

private class BackendRemoteCommitChangesViewModelRefresher(
  private val cs: CoroutineScope,
  private val requestsSink: MutableSharedFlow<in BackendChangesViewEvent.RefreshRequested>,
) {
  private val refreshRequestCounter = AtomicInteger(0)

  /**
   * Once backend was notified about refresh applied with the given counter,
   * all the pending callbacks having counter less than or equal to it will be executed.
   */
  private val lastAppliedRefresh = MutableStateFlow(-1)

  fun scheduleRefreshNow(callback: Runnable?) {
    val counter = refreshRequestCounter.incrementAndGet()
    if (callback != null) {
      cs.launch {
        withTimeout(REFRESH_TIMEOUT) {
          lastAppliedRefresh.first { it >= counter }
        }
        callback.run()
      }
    }

    cs.launch {
      requestsSink.emit(BackendChangesViewEvent.RefreshRequested(withDelay = false, counter))
    }
  }

  fun scheduleDelayedRefresh() {
    cs.launch {
      requestsSink.emit(BackendChangesViewEvent.RefreshRequested(withDelay = true, refreshRequestCounter.incrementAndGet()))
    }
  }

  fun refreshPerformed(counter: Int) {
    lastAppliedRefresh.update { maxOf(it, counter) }
  }
}

internal suspend fun Project.getRpcChangesView() = (serviceAsync<ChangesViewI>() as ChangesViewManager).changesView as RpcChangesViewProxy

private object BackendChangesViewValueIdType : BackendValueIdType<ChangesViewId, Unit>(::ChangesViewId)