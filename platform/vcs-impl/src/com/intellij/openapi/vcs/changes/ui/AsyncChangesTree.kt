// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.sync.OverflowSemaphore
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Dimension
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Function
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

/**
 * Call [shutdown] when the tree is no longer needed.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
abstract class AsyncChangesTree : ChangesTree {
  companion object {
    private val LOG = logger<AsyncChangesTree>()
  }

  protected abstract val changesTreeModel: AsyncChangesTreeModel

  val scope = CoroutineScope(SupervisorJob())

  private val updateSemaphore = OverflowSemaphore(overflow = BufferOverflow.DROP_OLDEST)
  private val _callbacks = Channel<PendingCallback>(capacity = Int.MAX_VALUE)
  private val _pendingColors = MutableSharedFlow<ChangesBrowserNode<*>>(extraBufferCapacity = 100,
                                                                        onBufferOverflow = BufferOverflow.DROP_OLDEST)

  private val lastFulfilledRequestId = MutableStateFlow(-1)
  private val lastRequestId = AtomicInteger()

  private val _busy = MutableStateFlow(false)
  val busy: Flow<Boolean> = _busy.asStateFlow()


  constructor(project: Project,
              showCheckboxes: Boolean,
              highlightProblems: Boolean)
    : this(project, showCheckboxes, highlightProblems, true)

  constructor(project: Project,
              showCheckboxes: Boolean,
              highlightProblems: Boolean,
              withSpeedSearch: Boolean)
    : super(project, showCheckboxes, highlightProblems, withSpeedSearch) {
    start()
  }

  fun shutdown() {
    scope.cancel()
  }


  override fun getPreferredScrollableViewportSize(): Dimension {
    val size = super.getPreferredSize()
    size.width = size.width.coerceAtLeast(JBUI.scale(350))
    size.height = size.height.coerceAtLeast(JBUI.scale(400))
    return size
  }

  var shouldShowBusyIconIfNeeded: Boolean = true
  override fun shouldShowBusyIconIfNeeded(): Boolean = shouldShowBusyIconIfNeeded

  override fun rebuildTree() {
    requestRefresh()
  }

  override fun rebuildTree(treeStateStrategy: TreeStateStrategy<*>) {
    requestRefresh(treeStateStrategy)
  }


  fun requestRefresh() {
    return requestRefreshImpl(treeStateStrategy = null,
                              onRefreshed = null)
  }

  fun requestRefresh(treeStateStrategy: TreeStateStrategy<*>) {
    return requestRefreshImpl(treeStateStrategy = treeStateStrategy,
                              onRefreshed = null)
  }

  fun requestRefresh(onRefreshed: Runnable?) {
    return requestRefreshImpl(treeStateStrategy = null,
                              onRefreshed = onRefreshed)
  }

  fun requestRefresh(treeStateStrategy: TreeStateStrategy<*>, onRefreshed: Runnable?) {
    return requestRefreshImpl(treeStateStrategy = treeStateStrategy,
                              onRefreshed = onRefreshed)
  }

  private fun requestRefreshImpl(treeStateStrategy: TreeStateStrategy<*>?,
                                 onRefreshed: Runnable?) {
    val refreshGrouping = grouping
    val requestId = lastRequestId.incrementAndGet()
    scope.launch {
      updateSemaphore.withPermit {
        refreshTreeModel(requestId, refreshGrouping, treeStateStrategy)
      }
    }

    if (onRefreshed != null) {
      invokeAfterRefresh(requestId, onRefreshed)
    }
  }

  fun invokeAfterRefresh(callback: Runnable) {
    invokeAfterRefresh(lastRequestId.get(), callback)
  }

  /**
   * Invoke callback on EDT when the refresh request was fulfilled. This might never happen if the tree was shut down.
   */
  private fun invokeAfterRefresh(requestId: Int, callback: Runnable) {
    val result = _callbacks.trySend(PendingCallback(requestId, callback))
    result.exceptionOrNull()?.let { LOG.error(it) }
  }

  private val treeEdtContext get() = Dispatchers.EDT + ModalityState.any().asContextElement()

  private fun start() {
    scope.launch(treeEdtContext) {
      _busy.collectLatest {
        updatePaintBusy(it)
      }
    }
    scope.launch(treeEdtContext) {
      while (true) {
        // We respect the order of callbacks scheduling,
        // thus the callback with smaller requestId can be delayed by earlier callback with bigger requestId.
        val callback = _callbacks.receive()
        if (callback.requestId > lastFulfilledRequestId.value) { // save on a context switch if it's already done
          lastFulfilledRequestId.first { fulfilledId -> // suspend while request is not yet completed
            callback.requestId <= fulfilledId
          }
        }
        handleCallback(callback)
      }
    }
    scope.launch {
      val repaintRequest = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
      launch(treeEdtContext, start = CoroutineStart.UNDISPATCHED) {
        repaintRequest.debounce(300).collect {
          repaint()
        }
      }

      _pendingColors.collect { node ->
        val wasChanged = readAction { node.cacheBackgroundColor(project) }
        if (wasChanged) {
          repaintRequest.emit(Unit)
        }
      }
    }
  }

  private suspend fun refreshTreeModel(requestId: Int,
                                       refreshGrouping: ChangesGroupingPolicyFactory,
                                       treeStateStrategy: TreeStateStrategy<*>?) {
    _busy.value = true
    try {
      val treeModel = changesTreeModel.buildTreeModel(refreshGrouping)
      withContext(treeEdtContext) {
        updateTreeModel(requestId, treeModel, treeStateStrategy)
      }
    }
    finally {
      _busy.value = false
    }
  }

  @RequiresEdt
  private suspend fun updateTreeModel(requestId: Int, treeModel: DefaultTreeModel, treeStateStrategy: TreeStateStrategy<*>?) {
    try {
      _pendingColors.resetReplayCache()

      blockingContext {
        if (treeStateStrategy != null) {
          updateTreeModel(treeModel, treeStateStrategy)
        }
        else {
          updateTreeModel(treeModel)
        }
      }
    }
    finally {
      lastFulfilledRequestId.value = requestId
    }
  }

  private suspend fun handleCallback(callback: PendingCallback) {
    try {
      coroutineToIndicator {
        callback.task.run()
      }
    }
    catch (ignore: ProcessCanceledException) {
    }
    catch (e: Throwable) {
      LOG.error(e)
    }
  }

  override fun isEmptyTextVisible(): Boolean {
    @Suppress("UNNECESSARY_SAFE_CALL") // called from super constructor
    return super.isEmptyTextVisible() && (_busy?.value != true)
  }

  @RequiresEdt
  private fun updatePaintBusy(isBusy: Boolean) {
    setPaintBusy(isBusy)
    repaint() // repaint empty text
  }

  override fun getFileColorForPath(path: TreePath): Color? {
    val node = path.lastPathComponent
    if (node is ChangesBrowserNode<*>) {
      if (!node.hasBackgroundColorCached()) {
        _pendingColors.tryEmit(node)
      }
      return node.backgroundColorCached
    }
    return null
  }

  private class PendingCallback(
    val requestId: Int,
    val task: java.lang.Runnable
  )
}

interface AsyncChangesTreeModel {
  suspend fun buildTreeModel(grouping: ChangesGroupingPolicyFactory): DefaultTreeModel
}

/**
 * [com.intellij.openapi.progress.ProgressIndicator]-friendly wrapper.
 */
abstract class SimpleAsyncChangesTreeModel : AsyncChangesTreeModel {
  @RequiresBackgroundThread
  abstract fun buildTreeModelSync(grouping: ChangesGroupingPolicyFactory): DefaultTreeModel

  final override suspend fun buildTreeModel(grouping: ChangesGroupingPolicyFactory): DefaultTreeModel {
    return coroutineToIndicator {
      buildTreeModelSync(grouping)
    }
  }

  companion object {
    @JvmStatic
    fun create(task: Function<ChangesGroupingPolicyFactory, DefaultTreeModel>): AsyncChangesTreeModel {
      return object : SimpleAsyncChangesTreeModel() {
        override fun buildTreeModelSync(grouping: ChangesGroupingPolicyFactory): DefaultTreeModel {
          return task.apply(grouping)
        }
      }
    }
  }
}

/**
 * [com.intellij.openapi.progress.ProgressIndicator]-friendly wrapper with two-step model updates.
 */
@ApiStatus.Internal
abstract class TwoStepAsyncChangesTreeModel<T>(val scope: CoroutineScope) : AsyncChangesTreeModel {
  private val deferredData: AtomicReference<Deferred<T>?> = AtomicReference()

  @RequiresBackgroundThread
  abstract fun fetchData(): T

  @RequiresBackgroundThread
  abstract fun buildTreeModelSync(data: T, grouping: ChangesGroupingPolicyFactory): DefaultTreeModel

  /**
   * Notify that data should be re-fetched during the next refresh.
   *
   * [AsyncChangesTree.requestRefresh] needs to be called explicitly afterwards.
   */
  fun invalidateData() {
    val oldJob = deferredData.getAndSet(null)
    oldJob?.cancel()
  }

  final override suspend fun buildTreeModel(grouping: ChangesGroupingPolicyFactory): DefaultTreeModel {
    val data = computeData()
    return coroutineToIndicator {
      buildTreeModelSync(data, grouping)
    }
  }

  private suspend fun computeData(): T {
    val oldJob = deferredData.get()
    if (oldJob != null) {
      return oldJob.await()
    }

    val newJob = scope.async { coroutineToIndicator { fetchData() } }
    deferredData.set(newJob)
    return newJob.await()
  }
}
