// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.hotswap

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.history.LocalHistory
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.rethrowControlFlowException
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.BulkAwareDocumentListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.Strings
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.containers.DisposableWrapperList
import com.intellij.xdebugger.hotswap.SourceFileChangesCollector
import com.intellij.xdebugger.hotswap.SourceFileChangesListener
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Service(Service.Level.APP)
private class ChangesProcessingService(coroutineScope: CoroutineScope) : Disposable.Default {
  private val collectors = DisposableWrapperList<SourceFileChangesCollectorImpl>()
  private val listenersCount = AtomicInteger()

  private val events = Channel<ChangesProcessingEvent>(Channel.UNLIMITED)
  private val allCache = Long2ObjectOpenHashMap<MutableMap<VirtualFile, FileContent?>>()
  private val queueSizeEstimate = AtomicInteger()
  private var lastSearchTimeNs = -1L

  init {
    coroutineScope.launch {
      events.consumeEach { processEvent(it) }
    }
  }

  private val documentListener = object : BulkAwareDocumentListener {
    override fun documentChangedNonBulk(event: DocumentEvent) = onChange(event.document)
    override fun bulkUpdateFinished(document: Document) = onChange(document)

    private fun onChange(document: Document) {
      queueSizeEstimate.incrementAndGet()
      val send = events.trySend(ChangesProcessingEvent.DocumentChanged(document))
      if (send.isFailure) {
        onDocumentChangeProcessed()
      }
    }
  }

  fun addCollector(collector: SourceFileChangesCollectorImpl) {
    Disposer.register(collector, Disposable {
      dropTimestamp(collector.lastTimestamp)
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

  fun dropTimestamp(timestamp: Long) {
    events.trySend(ChangesProcessingEvent.Clear(timestamp))
  }

  private suspend fun processEvent(event: ChangesProcessingEvent) {
    when (event) {
      is ChangesProcessingEvent.DocumentChanged -> {
        try {
          onDocumentChange(event.document)
        }
        catch (e: Throwable) {
          if (e is CancellationException || e is ControlFlowException) throw e
          logger.error(e)
        }
        finally {
          onDocumentChangeProcessed()
        }
      }
      is ChangesProcessingEvent.Clear -> allCache.remove(event.timestamp)
    }
  }

  private fun onDocumentChangeProcessed() {
    if (queueSizeEstimate.decrementAndGet() == 0) {
      lastSearchTimeNs = -1L
    }
  }

  private suspend fun onDocumentChange(document: Document) {
    val virtualFile = FileDocumentManager.getInstance().getFile(document) ?: return
    logger.debug { "Document changed: ${virtualFile}" }
    val filteredCollectors = collectors
      .filter { collector -> collector.filters.all { it.isApplicable(virtualFile) } }

    if (filteredCollectors.isEmpty()) {
      logger.debug { "Document change skipped as filtered: $virtualFile" }
      return
    }
    logger.debug { "Document change processing: $virtualFile" }
    val contentHash = Strings.stringHashCode(document.immutableCharSequence)
    val groupedByTimeStamp = filteredCollectors.groupBy { it.lastTimestamp }
    dropUnusedTimestamps(groupedByTimeStamp.keys)

    for ((timestamp, collectors) in groupedByTimeStamp) {
      val cache = allCache.computeIfAbsent(timestamp) { hashMapOf() }
      val doLocalHistorySearch = canDoLocalHistorySearch()
      val timeStartNs = System.nanoTime()
      val changeState = getChangeStateSinceLastReset(virtualFile, timestamp, contentHash, doLocalHistorySearch, cache)
      if (doLocalHistorySearch) {
        lastSearchTimeNs = System.nanoTime() - timeStartNs
      }
      for (collector in collectors) {
        collector.processDocumentChange(changeState)
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

  private sealed interface ChangesProcessingEvent {
    data class DocumentChanged(val document: Document) : ChangesProcessingEvent
    data class Clear(val timestamp: Long) : ChangesProcessingEvent
  }
}

@ApiStatus.Internal
fun interface SourceFileChangeFilter<T> {
  suspend fun isApplicable(change: T): Boolean
}

@ApiStatus.Internal
fun interface SourceFileChangeCompatibilityChecker {
  suspend fun getCompatibility(change: SourceFileChange): HotSwapChangesCompatibility
}

@ApiStatus.Internal
sealed interface HotSwapChangesCompatibility : Comparable<HotSwapChangesCompatibility> {
  val order: Int

  override fun compareTo(other: HotSwapChangesCompatibility): Int {
    return order - other.order
  }

  data object Irrelevant : HotSwapChangesCompatibility {
    override val order: Int
      get() = 0
  }

  data object Compatible : HotSwapChangesCompatibility {
    override val order: Int
      get() = 10
  }

  data object Unknown : HotSwapChangesCompatibility {
    override val order: Int
      get() = 20
  }

  data class Incompatible(val reason: @NlsSafe String) : HotSwapChangesCompatibility {
    override val order: Int
      get() = 30
  }
}

/**
 * Synthetic status that marks that there were no changes detected.
 */
private data object NoChanges : HotSwapChangesCompatibility {
  override val order: Int
    get() = -20
}

/**
 * Synthetic status that marks that there were no checkers provided.
 */
private data object NoChecks : HotSwapChangesCompatibility {
  override val order: Int
    get() = -10
}

/**
 * Synthetic status that marks that compatibility checks cannot be run.
 */
private data object CheckImpossible : HotSwapChangesCompatibility {
  override val order: Int
    get() = 15
}

private val logger = logger<SourceFileChangesCollectorImpl>()

/**
 * Default implementation of [SourceFileChangesCollector], that track modification of all available documents.
 */
@ApiStatus.Internal
class SourceFileChangesCollectorImpl(
  private val project: Project,
  coroutineScope: CoroutineScope,
  private val listener: SourceFileChangesListener,
  internal val filters: List<SourceFileChangeFilter<VirtualFile>>,
  private val compatibilityCheckers: List<SourceFileChangeCompatibilityChecker> = emptyList(),
) : SourceFileChangesCollector<VirtualFile>, Disposable {
  private val events = Channel<ProcessingJob>(Channel.UNLIMITED)
  private val lock = ReentrantLock()
  private val cs = coroutineScope.childScope("SourceFileChangesCollectorImpl")
  private val currentProcessing = ConcurrentHashMap<VirtualFile, Job>()

  /**
   * Set of statuses that were reported within the current changes scope (between the hot swaps, changes reset to original state or session start).
   */
  private val reportedStatuses = ConcurrentCollectionFactory.createConcurrentSet<HotSwapStatistics.HotSwapSourceCompatibilityStatus>()

  @Volatile
  private var currentChanges: MutableMap<VirtualFile, HotSwapChangesCompatibility> = hashMapOf()

  private val lastResetTimeStamp = AtomicLong(System.currentTimeMillis())
  val lastTimestamp: Long
    get() = lastResetTimeStamp.get()

  init {
    cs.launch {
      events.consumeEach { (file, job) ->
        try {
          job.start()
          job.join()
        }
        finally {
          currentProcessing.remove(file, job)
        }
      }
    }
    ChangesProcessingService.getInstance().addCollector(this)
  }

  override fun dispose() {
    cs.cancel()
  }

  override fun getChanges(): Set<VirtualFile> = lock.withLock { currentChanges.keys.toHashSet() }
  override fun resetChanges() {
    val previousTimeStamp = lastResetTimeStamp.getAndSet(System.currentTimeMillis())
    currentChanges = hashMapOf()
    reportedStatuses.clear()
    ChangesProcessingService.getInstance().dropTimestamp(previousTimeStamp)
  }

  internal fun processDocumentChange(change: ChangeState) {
    val file = change.file
    val job = cs.launch(start = CoroutineStart.LAZY) {
      processDocumentChangeInternal(change)
    }
    val previousJob = currentProcessing.put(file, job)
    previousJob?.cancel()
    val send = events.trySend(ProcessingJob(file, job))
    if (send.isFailure) {
      currentProcessing.remove(file, job)
      job.cancel()
    }
  }

  private data class ProcessingJob(val file: VirtualFile, val job: Job)

  private suspend fun processDocumentChangeInternal(change: ChangeState) {
    val file = change.file
    val currentChanges = currentChanges
    val compatibility = if (change.hasChanges) {
      val fileCompatibility = getCompatibility(file, change.oldContent)
      lock.withLock {
        currentChanges[file] = fileCompatibility
        currentChanges.calculateCompatibility()
      }
    }
    else {
      lock.withLock {
        currentChanges.remove(file)
        currentChanges.calculateCompatibility()
      }
    }

    currentCoroutineContext().ensureActive()
    reportSourceCompatibility(compatibility)
    when (val status = compatibility.toSourceFileChangesStatus()) {
      SourceFileChangesStatus.ChangesCanceled -> {
        logger.debug { "Document change reverted previous changes: $file" }
        listener.onChangesCanceled()
      }
      SourceFileChangesStatus.NewChanges -> {
        logger.debug { "Document change active: $file" }
        listener.onNewChanges()
      }
      is SourceFileChangesStatus.IncompatibleChanges -> {
        logger.debug { "Document change incompatible: $file (${status.reason})" }
        listener.onIncompatibleChanges(status.reason)
      }
    }
  }

  private fun Map<*, HotSwapChangesCompatibility>.calculateCompatibility(): HotSwapChangesCompatibility = values.maxOrNull() ?: NoChanges
  private fun HotSwapChangesCompatibility.toSourceFileChangesStatus(): SourceFileChangesStatus = when (this) {
    NoChanges -> SourceFileChangesStatus.ChangesCanceled
    is HotSwapChangesCompatibility.Incompatible -> SourceFileChangesStatus.IncompatibleChanges(this.reason)
    else -> SourceFileChangesStatus.NewChanges
  }

  private suspend fun getCompatibility(file: VirtualFile, oldContent: CharSequence?): HotSwapChangesCompatibility {
    if (compatibilityCheckers.isEmpty()) return NoChecks
    if (oldContent == null) return CheckImpossible
    val change = SourceFileChange(file, oldContent)
    try {
      return compatibilityCheckers.maxOf { checker -> checker.getCompatibility(change) }
    }
    catch (e: Throwable) {
      rethrowControlFlowException(e)
      logger.error("Error during compatibility check", e)
      return CheckImpossible
    }
  }

  private fun reportSourceCompatibility(compatibility: HotSwapChangesCompatibility) {
    val compatibilityStatus = when (compatibility) {
      NoChanges -> {
        reportedStatuses.clear()
        return
      }
      HotSwapChangesCompatibility.Irrelevant -> HotSwapStatistics.HotSwapSourceCompatibilityStatus.UNSUPPORTED
      HotSwapChangesCompatibility.Compatible -> HotSwapStatistics.HotSwapSourceCompatibilityStatus.COMPATIBLE
      NoChecks -> HotSwapStatistics.HotSwapSourceCompatibilityStatus.NO_CHECKS
      CheckImpossible -> HotSwapStatistics.HotSwapSourceCompatibilityStatus.CHECK_IMPOSSIBLE
      HotSwapChangesCompatibility.Unknown -> HotSwapStatistics.HotSwapSourceCompatibilityStatus.UNKNOWN
      is HotSwapChangesCompatibility.Incompatible -> HotSwapStatistics.HotSwapSourceCompatibilityStatus.INCOMPATIBLE
    }
    val shouldReport = reportedStatuses.add(compatibilityStatus)
    if (shouldReport) {
      HotSwapStatistics.logSourceCompatibilityDetected(project, compatibilityStatus)
    }
  }

  companion object {
    @TestOnly
    internal var customLocalHistory: LocalHistory? = null
  }
}

@ApiStatus.Internal
data class SourceFileChange(val file: VirtualFile, val oldContent: CharSequence)

private sealed interface SourceFileChangesStatus {
  data object NewChanges : SourceFileChangesStatus
  data class IncompatibleChanges(val reason: @NlsSafe String) : SourceFileChangesStatus
  data object ChangesCanceled : SourceFileChangesStatus
}

internal data class ChangeState(
  val file: VirtualFile,
  val hasChanges: Boolean,
  val oldContent: CharSequence?,
)

private data class FileContent(
  val content: CharSequence,
  val contentHash: Int,
)

private fun getChangeStateSinceLastReset(
  file: VirtualFile, lastTimestamp: Long, contentHash: Int,
  doLocalHistorySearch: Boolean,
  cache: MutableMap<VirtualFile, FileContent?>,
): ChangeState {
  val oldContent = run {
    if (cache.containsKey(file)) return@run cache[file]
    // com.intellij.history.integration.LocalHistoryImpl.getByteContent is a heavyweight operation with blocking read lock access.
    // When there are massive changes in a project (e.g., a git branch switch), such workload becomes critical for the UI.
    // To avoid this, the local history check is skipped when the number of modified files is too large.
    // As a consequence, reverting these changes may be incorrectly detected by this filter (they will still be shown as available),
    // but this is a minor issue.
    if (!doLocalHistorySearch) return ChangeState(file, hasChanges = true, oldContent = null)
    getContentBeforeLastReset(file, lastTimestamp).also { cache[file] = it }
  }
  if (oldContent == null) return ChangeState(file, hasChanges = true, oldContent = null)
  return ChangeState(file, hasChanges = contentHash != oldContent.contentHash, oldContent.content)
}

private fun getContentBeforeLastReset(file: VirtualFile, lastTimestamp: Long): FileContent? {
  val localHistory = SourceFileChangesCollectorImpl.customLocalHistory ?: LocalHistory.getInstance()
  val bytes = localHistory.getByteContent(file) { timestamp -> timestamp < lastTimestamp } ?: return null
  val content = LoadTextUtil.getTextByBinaryPresentation(bytes, file, false, false)
  return FileContent(content, Strings.stringHashCode(content))
}
