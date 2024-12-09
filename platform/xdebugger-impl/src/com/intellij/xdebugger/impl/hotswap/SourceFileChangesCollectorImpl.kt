// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.hotswap

import com.intellij.history.LocalHistory
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.Strings
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.SearchScope
import com.intellij.util.containers.DisposableWrapperList
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@Service(Service.Level.APP)
private class ChangesProcessingService(private val coroutineScope: CoroutineScope) : Disposable.Default {
  private val collectors = DisposableWrapperList<SourceFileChangesCollectorImpl>()
  private val listenersCount = AtomicInteger()

  @OptIn(ExperimentalCoroutinesApi::class)
  private val documentChangeDispatcher = Dispatchers.Default.limitedParallelism(1)
  private val allCache = Long2ObjectOpenHashMap<Object2IntOpenHashMap<VirtualFile>>()
  private val queueSizeEstimate = AtomicInteger()
  private var lastSearchTimeNs = -1L

  private val documentListener = object : DocumentListener {
    override fun documentChanged(event: DocumentEvent) {
      if (logger.isDebugEnabled) {
        logger.debug("Document changed: ${event.document}")
      }
      queueSizeEstimate.incrementAndGet()
      coroutineScope.launch(documentChangeDispatcher) {
        try {
          onDocumentChange(event.document)
        }
        finally {
          if (queueSizeEstimate.decrementAndGet() == 0) {
            lastSearchTimeNs = -1L
          }
        }
      }
    }
  }

  fun addCollector(collector: SourceFileChangesCollectorImpl) {
    Disposer.register(collector, Disposable {
      coroutineScope.launch(documentChangeDispatcher) {
        // clear cache
        allCache.remove(collector.lastResetTimeStamp)
      }
      if (listenersCount.decrementAndGet() == 0) {
        val eventMulticaster = EditorFactory.getInstance().eventMulticaster
        eventMulticaster.removeDocumentListener(documentListener)
      }
    })
    if (listenersCount.getAndIncrement() == 0) {
      val eventMulticaster = EditorFactory.getInstance().eventMulticaster
      eventMulticaster.addDocumentListener(documentListener, this)
    }
    collectors.add(collector, collector)
  }

  private suspend fun onDocumentChange(document: Document) = coroutineScope {
    val virtualFile = FileDocumentManager.getInstance().getFile(document) ?: return@coroutineScope
    val filteredCollectors = collectors
      .map { collector -> collector to async(Dispatchers.Default) { collector.filters.all { it.isApplicable(virtualFile) } } }
      .filter { it.second.await() }
      .map { it.first }
    if (filteredCollectors.isEmpty()) {
      if (logger.isDebugEnabled) {
        logger.debug("Document change skipped as filtered: $document")
      }
      return@coroutineScope
    }
    if (logger.isDebugEnabled) {
      logger.debug("Document change processing: $document")
    }
    val contentHash = Strings.stringHashCode(document.immutableCharSequence)
    val groupedByTimeStamp = filteredCollectors.groupBy { it.lastResetTimeStamp }
    dropUnusedTimestamps(groupedByTimeStamp.keys)

    for ((timestamp, collectors) in groupedByTimeStamp) {
      val cache = allCache.computeIfAbsent(timestamp) { Object2IntOpenHashMap() }
      val doLocalHistorySearch = canDoLocalHistorySearch()
      val timeStartNs = System.nanoTime()
      val hasChanges = hasChangesSinceLastReset(virtualFile, timestamp, contentHash, doLocalHistorySearch, cache)
      if (doLocalHistorySearch) {
        lastSearchTimeNs = System.nanoTime() - timeStartNs
      }
      for (collector in collectors) {
        collector.processDocumentChange(hasChanges, virtualFile, document)
      }
    }
  }

  /**
   * This check is introduced for optimization.
   * When there are a large number of changed files, each new file has a minimal effect on the overall status,
   * whether there are changes for HotSwap.
   * For the sake of performance, some checks (like local history) can be skipped.
   *
   * With this check applied, the search time for a set of changes should be no more than 1 second.
   */
  private fun canDoLocalHistorySearch(): Boolean =
    queueSizeEstimate.get() < 5 && lastSearchTimeNs < TimeUnit.MILLISECONDS.toNanos(200)

  private fun dropUnusedTimestamps(active: Set<Long>) {
    allCache.keys.minus(active).forEach { allCache.remove(it) }
  }

  companion object {
    fun getInstance() = service<ChangesProcessingService>()
  }
}

@ApiStatus.Internal
fun interface SourceFileChangeFilter<T> {
  suspend fun isApplicable(change: T): Boolean
}

private val logger = logger<SourceFileChangesCollectorImpl>()

/**
 * Default implementation of [SourceFileChangesCollector], that track modification of all available documents.
 */
@ApiStatus.Internal
class SourceFileChangesCollectorImpl(
  private val coroutineScope: CoroutineScope,
  internal val listener: SourceFileChangesListener,
  internal vararg val filters: SourceFileChangeFilter<VirtualFile>,
) : SourceFileChangesCollector<VirtualFile>, Disposable.Default {
  @OptIn(ExperimentalCoroutinesApi::class)
  private val limitedDispatcher = Dispatchers.Default.limitedParallelism(1)

  @Volatile
  private var currentChanges: MutableSet<VirtualFile> = hashSetOf()

  @Volatile
  internal var lastResetTimeStamp: Long = System.currentTimeMillis()
    private set

  init {
    ChangesProcessingService.getInstance().addCollector(this)
  }

  override fun getChanges(): Set<VirtualFile> = currentChanges
  override fun resetChanges() {
    lastResetTimeStamp = System.currentTimeMillis()
    currentChanges = hashSetOf()
  }

  internal fun processDocumentChange(
    hasChangesSinceLastReset: Boolean,
    file: VirtualFile, document: Document,
  ) = coroutineScope.launch(limitedDispatcher) {
    val currentChanges = currentChanges
    if (hasChangesSinceLastReset) {
      currentChanges.add(file)
    }
    else {
      currentChanges.remove(file)
    }

    val isEmpty = currentChanges.isEmpty()
    if (isEmpty) {
      if (logger.isDebugEnabled) {
        logger.debug("Document change reverted previous changes: $document")
      }
      listener.onChangesCanceled()
    }
    else {
      if (logger.isDebugEnabled) {
        logger.debug("Document change active: $document")
      }
      listener.onNewChanges()
    }
  }

  companion object {
    @TestOnly
    internal var customLocalHistory: LocalHistory? = null
  }
}

private fun hasChangesSinceLastReset(
  file: VirtualFile, lastTimestamp: Long, contentHash: Int,
  doLocalHistorySearch: Boolean,
  cache: Object2IntOpenHashMap<VirtualFile>,
): Boolean {
  val oldHash = run {
    if (file in cache) return@run cache.getInt(file)
    // com.intellij.history.integration.LocalHistoryImpl.getByteContent is a heavyweight operation with blocking read lock access.
    // When there are massive changes in a project (e.g., a git branch switch), such workload becomes critical for the UI.
    // To avoid this, the local history check is skipped when the number of modified files is too large.
    // As a consequence, reverting these changes may be incorrectly detected by this filter (they will still be shown as available),
    // but this is a minor issue.
    if (!doLocalHistorySearch) return true
    getContentHashBeforeLastReset(file, lastTimestamp).also { cache.put(file, it) }
  }
  if (oldHash == -1) return true
  return contentHash != oldHash
}

private fun getContentHashBeforeLastReset(file: VirtualFile, lastTimestamp: Long): Int {
  val localHistory = SourceFileChangesCollectorImpl.customLocalHistory ?: LocalHistory.getInstance()
  val bytes = localHistory.getByteContent(file) { timestamp -> timestamp < lastTimestamp } ?: return -1
  val content = LoadTextUtil.getTextByBinaryPresentation(bytes, file, false, false)
  return Strings.stringHashCode(content)
}

@ApiStatus.Internal
class SearchScopeFilter(private val searchScope: SearchScope) : SourceFileChangeFilter<VirtualFile> {
  override suspend fun isApplicable(change: VirtualFile): Boolean {
    return readAction { searchScope.contains(change) }
  }
}
