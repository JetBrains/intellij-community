// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs

import com.intellij.ide.IdeCoreBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.*
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.diagnostic.FrequentEventDetector
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.monitoring.VfsUsageCollector
import com.intellij.util.SmartList
import com.intellij.util.concurrency.CoroutineDispatcherBackedExecutor
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.intellij.util.concurrency.createBoundedTaskExecutor
import com.intellij.util.io.storage.HeavyProcessLatch
import com.intellij.util.ui.EDT
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer

@ApiStatus.Internal
class RefreshQueueImpl(coroutineScope: CoroutineScope) : RefreshQueue(), Disposable {
  private val myQueue: CoroutineDispatcherBackedExecutor = createBoundedTaskExecutor("RefreshQueue Pool", coroutineScope)
  private val myEventProcessingQueue: CoroutineDispatcherBackedExecutor = createBoundedTaskExecutor("Async Refresh Event Processing", coroutineScope)

  private val myRefreshIndicator = RefreshProgress.create()
  private val mySessions: MutableSet<RefreshSessionImpl> = Collections.synchronizedSet(HashSet())
  private val myEventCounter = FrequentEventDetector(100, 100, FrequentEventDetector.Level.WARN)
  private var myActivityCounter = 0

  fun execute(session: RefreshSessionImpl) {
    val app: ApplicationEx?
    if (session.isAsynchronous) {
      queueSession(session, session.modality)
    }
    else if ((ApplicationManagerEx.getApplicationEx().also { app = it }).isWriteIntentLockAcquired() && EDT.isCurrentThreadEdt()) {
      (TransactionGuard.getInstance() as TransactionGuardImpl).assertWriteActionAllowed()
      val events = runRefreshSession(session, -1L)
      fireEvents(events, session)
    }
    else if (EDT.isCurrentThreadEdt()) {
      LOG.error("Do not perform a synchronous refresh on naked EDT (without WIL) (causes deadlocks if there are events to fire)")
    }
    else if (app!!.holdsReadLock()) {
      LOG.error("Do not perform a synchronous refresh under read lock (causes deadlocks if there are events to fire)")
    }
    else {
      queueSession(session, session.modality)
      session.waitFor()
    }
  }

  private fun queueSession(session: RefreshSessionImpl, modality: ModalityState) {
    if (LOG.isDebugEnabled()) LOG.debug("Queue session with id=" + session.hashCode())
    if (session.isEventSession && !session.isAsynchronous) {
      processEvents(session, session.modality, runRefreshSession(session, -1L))
    }
    else {
      val queuedAt = System.nanoTime()
      myQueue.execute {
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
  ) {
    var t = System.nanoTime()
    session.fireEvents(events, changeAppliers, true)
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
          doFireEvents(session, evTimeInQueue, evListenerTime, evRetries, data.first, data.second)
        }
        .submit(myEventProcessingQueue)
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

  override fun createSession(async: Boolean, recursively: Boolean, finishRunnable: Runnable?, state: ModalityState): RefreshSession {
    return RefreshSessionImpl(async, recursively, false, finishRunnable, state)
  }

  override fun processEvents(async: Boolean, events: List<VFileEvent>) {
    RefreshSessionImpl(async, events).launch()
  }

  @ApiStatus.Internal
  fun createBackgroundRefreshSession(files: List<VirtualFile>): RefreshSession {
    return RefreshSessionImpl(files)
  }

  override fun dispose() {
    synchronized(mySessions) {
      for (session in mySessions) {
        session.cancel()
      }
    }
    myEventProcessingQueue.cancel()
    myQueue.cancel()
  }

  companion object {
    private val LOG = Logger.getInstance(RefreshQueue::class.java)

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
        return !refreshQueue.mySessions.isEmpty() || !refreshQueue.myQueue.isEmpty()
      }

    @JvmStatic
    @get:ApiStatus.Internal
    val isEventProcessingInProgress: Boolean
      get() {
        val refreshQueue = getInstance() as RefreshQueueImpl
        return !refreshQueue.myEventProcessingQueue.isEmpty()
      }

    @TestOnly
    @JvmStatic
    fun setTestListener(testListener: Consumer<in VirtualFile?>?) {
      assert(ApplicationManager.getApplication().isUnitTestMode())
      RefreshWorker.ourTestListener = testListener
    }
  }
}
