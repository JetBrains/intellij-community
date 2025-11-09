// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.changes.viewModel

import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.ui.ChangesListView
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.kernel.ids.BackendValueIdType
import com.intellij.platform.kernel.ids.storeValueGlobally
import com.intellij.platform.vcs.impl.shared.rpc.BackendChangesViewEvent
import com.intellij.platform.vcs.impl.shared.rpc.ChangesViewApi
import com.intellij.ui.split.createComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
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
internal class RpcChangesViewProxy(private val project: Project, scope: CoroutineScope) : ChangesViewProxy(scope) {
  private val treeView: ChangesListView by lazy { LocalChangesListView(project) }

  private val _eventsForFrontend =
    MutableSharedFlow<BackendChangesViewEvent>(extraBufferCapacity = DEFAULT_BUFFER_SIZE, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  private val refresher = BackendRemoteCommitChangesViewModelRefresher(scope, _eventsForFrontend)

  val eventsForFrontend: SharedFlow<BackendChangesViewEvent> = _eventsForFrontend.asSharedFlow()

  val inclusionModel = MutableStateFlow<InclusionModel?>(null)

  private var _panel: JComponent? = null
  override val panel: JComponent
    get() = _panel ?: error("Panel is not initialized yet")

  override val inclusionChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  override fun setInclusionModel(model: InclusionModel?) {
    inclusionModel.value = model
  }

  override fun initPanel() {
    val id = storeValueGlobally(scope, Unit, BackendChangesViewValueIdType)
    _panel = ChangesViewSplitComponentBinding.createComponent(project, scope, id)
  }

  override fun setToolbarHorizontal(horizontal: Boolean) {
  }

  override fun isModelUpdateInProgress(): Boolean = false

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

  override fun setShowCheckboxes(value: Boolean) {
    _eventsForFrontend.tryEmit(BackendChangesViewEvent.ToggleCheckboxes(value))
  }

  override fun getDisplayedChanges(): List<Change> = emptyList()

  override fun getIncludedChanges(): List<Change> = inclusionModel.value?.getInclusion()?.filterIsInstance<Change>().orEmpty()

  override fun getDisplayedUnversionedFiles(): List<FilePath> = emptyList()

  override fun getIncludedUnversionedFiles(): List<FilePath> = emptyList()

  override fun expand(item: Any) {
  }

  override fun select(item: Any) {
  }

  override fun selectFirst(items: Collection<Any>) {
  }

  override fun selectFile(vFile: VirtualFile?) {
  }

  override fun selectChanges(changes: List<Change>) {
  }

  override fun getTree(): ChangesListView = treeView

  fun inclusionChanged() {
    inclusionChanged.tryEmit(Unit)
  }

  fun refreshPerformed(counter: Int) = refresher.refreshPerformed(counter)
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