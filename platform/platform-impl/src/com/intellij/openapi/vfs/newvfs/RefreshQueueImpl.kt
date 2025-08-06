// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs

import com.intellij.ide.IdeCoreBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.*
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.diagnostic.FrequentEventDetector
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.monitoring.VfsUsageCollector
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.SmartList
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.intellij.util.io.storage.HeavyProcessLatch
import com.intellij.util.ui.EDT
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer
import kotlin.time.TimeSource

@ApiStatus.Internal
class RefreshQueueImpl(coroutineScope: CoroutineScope) : RefreshQueue(), Disposable {
  /**
   * We maintain an invariant that no more than one [RefreshWorker] can run at any moment of time.
   */
  private val eventScanningScope: CoroutineScope = coroutineScope.childScope("RefreshQueue pool", Dispatchers.Default.limitedParallelism(1))

  /**
   * Since scanning does not initiate write actions (hence it does not depend on modalities), we can protect it with a global Semaphore
   */
  private val eventScanSemaphore: Semaphore = Semaphore(1)

  private val eventProcessingScope: CoroutineScope = coroutineScope.childScope("RefreshQueue pool", Dispatchers.Default.limitedParallelism(1))

  private val myRefreshIndicator = RefreshProgress.create()
  private val mySessions: MutableSet<RefreshSessionImpl> = Collections.synchronizedSet(HashSet())
  private val myEventCounter = FrequentEventDetector(100, 100, FrequentEventDetector.Level.WARN)
  private var myActivityCounter = 0
  private val parallelizationCache = ConcurrentHashMap<ModalityState, Pair<Semaphore, Int>>()

  internal fun execute(session: RefreshSessionImpl) {
    if (session.isAsynchronous) {
      if (isVfsRefreshInBackgroundWriteActionAllowed() && session.modality == ModalityState.nonModal()) {
        queueAsyncSessionWithCoroutines(session)
      }
      else {
        // An asynchronous refresh launched under old modal progress (`runProcessWithProgressSynchronously`) can outlive its modality state
        // This violates the structured concurrency principles that are behind coroutine-based refresh, hence we fall back to old NBRA-based refresh.
        queueSession(session, session.modality)
      }
    }
    else if (EDT.isCurrentThreadEdt()) {
      (TransactionGuard.getInstance() as TransactionGuardImpl).assertWriteActionAllowed()
      val events = runRefreshSession(session, -1L)
      fireEvents(events, session)
    }
    else if (ApplicationManager.getApplication().holdsReadLock()) {
      LOG.error("Do not perform a synchronous refresh under read lock (causes deadlocks if there are events to fire)")
    }
    else {
      queueSession(session, session.modality)
      session.waitFor()
    }
  }

  private fun CoroutineScope.launchWithPermit(semaphore: Semaphore, action: suspend CoroutineScope.() -> Unit) {
    launch {
      semaphore.withPermit {
        coroutineScope {
          action()
        }
      }
    }
  }

  /**
   * The clients may spam with refresh a lot, which may have a negative effect on performance due to the amount of concurrent refreshes.
   * With synchronous refresh, there is a limitation on parallelism of the refresh thread.
   * With coroutines, we rather need to limit the concurrency of parallel refreshes, hence we are using semaphores.
   */
  private suspend fun <T> executeWithParallelizationGuard(session: RefreshSessionImpl, action: suspend () -> T): T {
    return executeWithParallelizationGuard(session.modality, parallelizationCache, action)
  }

  /**
   * Executes session with legacy non-blocking read action and write action on EDT
   */
  private fun queueSession(session: RefreshSessionImpl, modality: ModalityState) {
    if (LOG.isDebugEnabled()) LOG.debug("Queue session with id=" + session.hashCode())
    if (session.isEventSession && !session.isAsynchronous) {
      processEvents(session, session.modality, runRefreshSession(session, -1L))
    }
    else {
      val queuedAt = System.nanoTime()
      eventScanningScope.launchWithPermit(eventScanSemaphore) {
        val timeInQueue = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - queuedAt)
        startIndicator(IdeCoreBundle.message("file.synchronize.progress"))
        val events = AtomicReference<Collection<VFileEvent>>()
        try {
          val title = IdeCoreBundle.message("progress.title.doing.file.refresh.0", session)
          HeavyProcessLatch.INSTANCE.performOperation(HeavyProcessLatch.Type.Syncing, title,
                                                      Runnable { events.set(runRefreshSession(session, timeInQueue)) })
        }
        finally {
          stopIndicator()
          processEvents(session, modality, events.get())
        }
      }
    }
    myEventCounter.eventHappened(session)
  }

  internal suspend fun executeSuspending(session: RefreshSessionImpl) {
    // suspending vfs refresh works in the context of the caller
    // however, we must maintain an invariant that no more than one scanning part of refresh is running
    // hence we limit ourselves with a semaphore
    val events = eventScanSemaphore.withPermit {
      collectEventsSuspending(session, -1L)
    }
    executeWithParallelizationGuard(session) {
      processEventsSuspending(session, events)
    }
  }

  private suspend fun collectEventsSuspending(session: RefreshSessionImpl, timeInQueue: Long): Collection<VFileEvent> {
    return withBackgroundProgress(ProjectManager.getInstance().defaultProject, IdeCoreBundle.message("file.synchronize.progress"), TaskCancellation.nonCancellable()) {
      runRefreshSession(session, timeInQueue)
    }
  }

  /**
   * This session is queued asynchronously with suspending read action and background write action
   */
  private fun queueAsyncSessionWithCoroutines(session: RefreshSessionImpl) {
    check(session.isAsynchronous) {
      "Only asynchronous sessions can be queued with coroutines"
    }
    check(session.modality == ModalityState.nonModal()) {
      "Only sessions in non-modal context can be queued with coroutines. " +
      "If you need to run your sessions with non-trivial modality, consider using `launchOnShow` for the component and `launch` for the session."
    }
    val queuedAt = TimeSource.Monotonic.markNow()
    eventScanningScope.launchWithPermit(eventScanSemaphore) {
      val timeInQueue = queuedAt.elapsedNow()
      val events = collectEventsSuspending(session, timeInQueue.inWholeNanoseconds)
      eventProcessingScope.launch {
        executeWithParallelizationGuard(session) {
          processEventsSuspending(session, events)
        }
      }
    }
  }

  private suspend fun processEventsSuspending(session: RefreshSessionImpl, events: Collection<VFileEvent>) {
    val evQueuedAt = System.nanoTime()
    val evTimeInQueue = AtomicLong(-1)
    val evListenerTime = AtomicLong(-1)
    val evRetries = AtomicLong(0)
    startIndicator(IdeCoreBundle.message("async.events.progress"))
    try {
      readAndBackgroundWriteAction {
        myRefreshIndicator.checkCanceled()
        val (events, changeAppliers) = collectChangeAppliersInReadAction(session, events, evQueuedAt, evTimeInQueue, evRetries, evListenerTime)
        writeAction {
          doFireEvents(session, evTimeInQueue, evListenerTime, evRetries, events, changeAppliers, true)
        }
      }
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Throwable) {
      LOG.error(e)
    }
    finally {
      stopIndicator()
    }
  }

  @RequiresReadLock
  @RequiresBackgroundThread
  private fun collectChangeAppliersInReadAction(
    session: RefreshSessionImpl,
    events: Collection<VFileEvent>,
    evQueuedAt: Long,
    evTimeInQueue: AtomicLong,
    evRetries: AtomicLong,
    evListenerTime: AtomicLong,
  ): Pair<List<CompoundVFileEvent>, List<AsyncFileListener.ChangeApplier>> {
    if (LOG.isDebugEnabled()) LOG.debug("Start non-blocking action for session with id=" + session.hashCode())
    evTimeInQueue.compareAndSet(-1, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - evQueuedAt))
    evRetries.incrementAndGet()
    val t = System.nanoTime()
    try {
      val result: Pair<List<CompoundVFileEvent>, List<AsyncFileListener.ChangeApplier>> = runAsyncListeners(events)
      if (LOG.isDebugEnabled()) LOG.debug("Successful finish of non-blocking read action for session with id=" + session.hashCode())
      return result
    }
    finally {
      if (LOG.isDebugEnabled()) LOG.debug("Final block of non-blocking read action for  session with id=" + session.hashCode())
      evListenerTime.addAndGet(System.nanoTime() - t)
    }
  }

  @RequiresWriteLock
  private fun doFireEvents(
    session: RefreshSessionImpl,
    evTimeInQueue: AtomicLong,
    evListenerTime: AtomicLong,
    evRetries: AtomicLong,
    events: List<CompoundVFileEvent>,
    changeAppliers: List<AsyncFileListener.ChangeApplier>,
    backgroundWriteAction: Boolean,
  ) {
    var t = System.nanoTime()
    if (backgroundWriteAction) {
      session.fireEventsInBackgroundWriteAction(events, changeAppliers)
    }
    else {
      session.fireEvents(events, changeAppliers, true)
    }
    t = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t)
    VfsUsageCollector.logEventProcessing(evTimeInQueue.toLong(), TimeUnit.NANOSECONDS.toMillis(evListenerTime.toLong()), evRetries.toInt(), t, events.size)
  }

  private fun processEvents(session: RefreshSessionImpl, modality: ModalityState, events: Collection<VFileEvent>) {
    if (Registry.`is`("vfs.async.event.processing", true) && !events.isEmpty()) {
      val evQueuedAt = System.nanoTime()
      val evTimeInQueue = AtomicLong(-1)
      val evListenerTime = AtomicLong(-1)
      val evRetries = AtomicLong(0)
      startIndicator(IdeCoreBundle.message("async.events.progress"))
      ReadAction
        .nonBlocking<Pair<List<CompoundVFileEvent>, List<AsyncFileListener.ChangeApplier>>> {
          collectChangeAppliersInReadAction(session, events, evQueuedAt, evTimeInQueue, evRetries, evListenerTime)
        }
        .expireWith(this)
        .wrapProgress(myRefreshIndicator)
        .finishOnUiThread(modality) { data: Pair<List<CompoundVFileEvent>, List<AsyncFileListener.ChangeApplier>> ->
          doFireEvents(session, evTimeInQueue, evListenerTime, evRetries, data.first, data.second, false)
        }
        .submit {
          eventProcessingScope.launch {
            executeWithParallelizationGuard(session) {
              it.run()
            }
          }
        }
        .onProcessed { stopIndicator() }
        .onError(Consumer { t: Throwable ->
          if (!myRefreshIndicator.isCanceled()) {
            LOG.error(t)
          }
        })
    }
    else {
      AppUIExecutor.onWriteThread(modality).later().submit(Runnable { fireEvents(events, session) })
    }
  }

  @Synchronized
  private fun startIndicator(text: @NlsContexts.ProgressText String?) {
    if (myActivityCounter++ == 0) {
      myRefreshIndicator.setText(text)
      myRefreshIndicator.start()
    }
  }

  @Synchronized
  private fun stopIndicator() {
    if (--myActivityCounter == 0) {
      myRefreshIndicator.stop()
    }
  }

  private fun runRefreshSession(session: RefreshSessionImpl, timeInQueue: Long): Collection<VFileEvent> {
    try {
      mySessions.add(session)
      return session.scan(timeInQueue)
    }
    finally {
      mySessions.remove(session)
    }
  }

  override fun createSession(async: Boolean, recursive: Boolean, finishRunnable: Runnable?, state: ModalityState): RefreshSession {
    return RefreshSessionImpl(async, recursive, false, finishRunnable, state)
  }

  override fun processEvents(async: Boolean, events: List<VFileEvent>) {
    RefreshSessionImpl(async, events).launch()
  }

  override fun dispose() {
    synchronized(mySessions) {
      for (session in mySessions) {
        session.cancel()
      }
    }
    eventScanningScope.children().forEach { it.cancel() }
    eventProcessingScope.children().forEach { it.cancel() }
  }

  companion object {
    private val LOG = Logger.getInstance(RefreshQueue::class.java)

    private fun CoroutineScope.children(): List<Job> {
      return coroutineContext.job.children.toList()
    }

    private fun fireEvents(events: Collection<VFileEvent>, session: RefreshSessionImpl) {
      var t = System.nanoTime()
      val compoundEvents = events.map { event: VFileEvent -> CompoundVFileEvent(event) }
      session.fireEvents(compoundEvents, listOf(), false)
      t = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t)
      VfsUsageCollector.logEventProcessing(-1L, -1L, -1, t, compoundEvents.size)
    }

    private fun runAsyncListeners(events: Collection<VFileEvent>): Pair<List<CompoundVFileEvent>, List<AsyncFileListener.ChangeApplier>> {
      val compoundEvents = events.mapNotNull { e: VFileEvent? ->
        val file = if (e is VFileCreateEvent) e.parent else e!!.getFile()
        if (file == null || file.isValid()) CompoundVFileEvent(
          e)
        else null
      }
      val allEvents = compoundEvents.flatMap { e: CompoundVFileEvent ->
        val toMap = SmartList(e.getInducedEvents())
        toMap.add(e.fileEvent)
        toMap
      }
      return Pair(compoundEvents, AsyncEventSupport.runAsyncListeners(allEvents))
    }

    @JvmStatic
    val isRefreshInProgress: Boolean
      get() {
        val refreshQueue = getInstance() as RefreshQueueImpl
        return !refreshQueue.mySessions.isEmpty() || refreshQueue.eventScanningScope.children().isNotEmpty()
      }

    @JvmStatic
    @get:ApiStatus.Internal
    val isEventProcessingInProgress: Boolean
      get() {
        val refreshQueue = getInstance() as RefreshQueueImpl
        return refreshQueue.eventProcessingScope.children().isNotEmpty()
      }

    @TestOnly
    @JvmStatic
    fun setTestListener(testListener: Consumer<in VirtualFile?>?) {
      assert(ApplicationManager.getApplication().isUnitTestMode())
      RefreshWorker.ourTestListener = testListener
    }

    /**
     * DO NOT use [MutableMap] here. We rely on atomic [ConcurrentMap.getOrPut].
     */
    @VisibleForTesting
    suspend fun <T : Any, R> executeWithParallelizationGuard(key: T, map: ConcurrentMap<T, Pair<Semaphore, Int>>, action: suspend () -> R): R {
      var currentValue: Pair<Semaphore, Int>
      // this is a loop similar to compare-and-swap,
      // we try to reference-count simultaneously operating refreshes for each given key
      do {
        currentValue = map.getOrPut(key) { Pair(Semaphore(1), 0) }
        val newValue = currentValue.copy(second = currentValue.second + 1)
        val replacementResult = map.replace(key, currentValue, newValue)
      } while (!replacementResult)
      val semaphore = currentValue.first
      try {
        return semaphore.withPermit {
          action()
        }
      } finally {
        do {
          currentValue = requireNotNull(map[key])
          val newValue = currentValue.copy(second = currentValue.second - 1)
          val replacementResult = map.replace(key, currentValue, newValue)
        } while (!replacementResult)
        map.remove(key, semaphore to 0)
      }
    }
  }

  override suspend fun refresh(recursive: Boolean, files: List<VirtualFile>) {
    @Suppress("ForbiddenInSuspectContextMethod")
    val session = createSession(false, recursive, null, ModalityState.defaultModalityState())
    session.addAllFiles(files)
    if (isVfsRefreshInBackgroundWriteActionAllowed()) {
      session.executeInBackgroundWriteAction()
    } else {
      currentCoroutineContext().job.invokeOnCompletion {
        session.cancel()
      }
      withContext(Dispatchers.Default) {
        session.launch()
      }
    }
  }

  override suspend fun processEvents(events: List<VFileEvent>) {
    @Suppress("ForbiddenInSuspectContextMethod")
    val session = createSession(false, false, null, ModalityState.defaultModalityState())
    session.addEvents(events)
    if (isVfsRefreshInBackgroundWriteActionAllowed()) {
      session.executeInBackgroundWriteAction()
    } else {
      currentCoroutineContext().job.invokeOnCompletion {
        session.cancel()
      }
      withContext(Dispatchers.Default) {
        session.launch()
      }
    }
  }

  /**
   * @return true if VFS refresh is allowed to run in background write action
   */
  private fun isVfsRefreshInBackgroundWriteActionAllowed(): Boolean {
    return useBackgroundWriteAction && Registry.`is`("vfs.refresh.use.background.write.action")
  }
}
