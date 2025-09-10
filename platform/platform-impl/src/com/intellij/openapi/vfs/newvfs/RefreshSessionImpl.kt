// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs

import com.intellij.codeInsight.daemon.impl.FileStatusMap
import com.intellij.diagnostic.PerformanceWatcher
import com.intellij.diagnostic.PerformanceWatcher.Companion.takeSnapshot
import com.intellij.ide.IdeCoreBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.impl.InternalThreading
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.ProgressIndicatorWithDelayedPresentation
import com.intellij.openapi.progress.withWriteActionTitle
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.impl.VirtualFileManagerImpl
import com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.monitoring.VfsUsageCollector
import com.intellij.util.SystemProperties
import com.intellij.util.concurrency.Semaphore
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.intellij.util.progress.waitForMaybeCancellable
import com.intellij.util.ui.EDT
import org.jetbrains.annotations.ApiStatus
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import kotlin.concurrent.Volatile
import kotlin.math.min

@ApiStatus.Internal
internal class RefreshSessionImpl internal constructor(
  val isAsynchronous: Boolean,
  private val myIsRecursive: Boolean,
  private val myIsBackground: Boolean,
  internal val myFinishRunnable: Runnable?,
  modality: ModalityState,
) : RefreshSession() {
  val modality: ModalityState = getSaneModalityState(modality)
  private val myStartTrace: Throwable?
  private val mySemaphore = Semaphore()

  private var myWorkQueue: MutableList<VirtualFile> = ArrayList<VirtualFile>()
  private val myEvents: MutableList<VFileEvent> = ArrayList<VFileEvent>()

  @Volatile
  private var myWorker: RefreshWorker? = null

  @Volatile
  private var myCancelled = false

  @Volatile
  private var myLaunched = false

  @Volatile
  private var myEventCount = 0

  init {
    TransactionGuard.getInstance().assertWriteSafeContext(this.modality)
    val app = ApplicationManager.getApplication()
    myStartTrace = if (app.isUnitTestMode() && (isAsynchronous || !app.isDispatchThread())) Throwable() else null
    if (LOG.isDebugEnabled()) {
      LOG.debug("RefreshSessionImpl created. Trace.", Throwable())
    }
  }

  internal constructor(files: List<VirtualFile>) : this(false, true, true, null, ModalityState.defaultModalityState()) {
    addAllFiles(files)
  }

  internal constructor(async: Boolean, events: List<VFileEvent>) : this(async, false, false, null, ModalityState.defaultModalityState()) {
    val filtered: List<VFileEvent> = events.filter { obj: Any? -> Objects.nonNull(obj) }
    if (filtered.size < events.size) LOG.error("The list of events must not contain null elements")
    addEvents(filtered)
  }

  override fun addFile(file: VirtualFile) {
    checkState()
    doAddFile(file)
  }

   override fun addAllFiles(files: Collection<VirtualFile>) {
    checkState()
    for (file in files) doAddFile(file)
  }

  private fun checkState() {
    check(!myCancelled) { "Already cancelled" }
    check(!myLaunched) { "Already launched" }
  }

  private fun doAddFile(file: VirtualFile) {
    if (file is NewVirtualFile) {
      myWorkQueue.add(file)
    }
    else {
      LOG.debug("skipped: " + file + " / " + file.javaClass)
    }
  }

  override fun launch() {
    if (prepareExecution()) return
    (RefreshQueue.getInstance() as RefreshQueueImpl).execute(this)
  }

  override suspend fun executeInBackgroundWriteAction() {
    if (prepareExecution()) return
    (RefreshQueue.getInstance() as RefreshQueueImpl).executeSuspending(this)
  }

  fun prepareExecution(): /* if nothing to do */ Boolean {
    checkState()
    if (myWorkQueue.isEmpty() && myEvents.isEmpty()) {
      if (myFinishRunnable == null) return true
      LOG.warn(Exception("no files to refresh"))
    }
    myLaunched = true
    mySemaphore.down()
    return false
  }

  val isEventSession: Boolean
    get() = myWorkQueue.isEmpty() && !myEvents.isEmpty()

  fun scan(timeInQueue: Long): Collection<VFileEvent> {
    if (myWorkQueue.isEmpty()) return myEvents
    val workQueue = myWorkQueue
    myWorkQueue = mutableListOf()
    val forceRefresh = !myIsRecursive && !this.isAsynchronous // shallow sync refresh (e.g., project config files on open)

    val fs = LocalFileSystem.getInstance()
    if (!forceRefresh && fs is LocalFileSystemImpl) {
      fs.markSuspiciousFilesDirty(workQueue)
    }

    if (LOG.isTraceEnabled) LOG.trace("scanning $workQueue")

    var t = System.nanoTime()
    var snapshot: PerformanceWatcher.Snapshot? = null
    var types: MutableMap<String?, Int?>? = null
    if (DURATION_REPORT_THRESHOLD_MS > 0) {
      snapshot = takeSnapshot()
      types = HashMap<String?, Int?>()
    }

    val refreshRoots = ArrayList<NewVirtualFile>(workQueue.size)
    for (file in workQueue) {
      if (myCancelled) break

      val nvf = file as NewVirtualFile
      if (forceRefresh) {
        nvf.markDirty()
      }
      if (!nvf.isDirty()) {
        continue
      }
      refreshRoots.add(nvf)

      if (types != null) {
        val type = if (!file.isDirectory()) "file" else if (file.getFileSystem() is ArchiveFileSystem) "arc" else "dir"
        types[type] = types.getOrDefault(type, 0)!! + 1
      }
    }

    var count = 0
    val events = ArrayList<VFileEvent?>()
    do {
      if (myCancelled) break
      if (LOG.isTraceEnabled) LOG.trace("try=$count")

      val worker = RefreshWorker(refreshRoots, myIsRecursive)
      myWorker = worker
      events.addAll(worker.scan())
      myWorker = null

      count++
      if (LOG.isTraceEnabled) LOG.trace("events=${events.size}")
    }
    while (myIsRecursive && !myIsBackground && count < RETRY_LIMIT && workQueue.any { f -> (f as NewVirtualFile).isDirty() })

    t = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t)
    var localRoots = 0
    var archiveRoots = 0
    var otherRoots = 0
    for (file in refreshRoots) {
      if (file.getFileSystem() is LocalFileSystem) localRoots++
      else if (file.getFileSystem() is ArchiveFileSystem) archiveRoots++
      else otherRoots++
    }
    VfsUsageCollector.logRefreshSession(myIsRecursive, localRoots, archiveRoots, otherRoots, myCancelled, timeInQueue, t, count)
    if (LOG.isTraceEnabled) {
      LOG.trace((if (myCancelled) "cancelled, " else "done, ") + t + " ms, tries " + count + ", events " + events)
    }
    else if (snapshot != null && t > DURATION_REPORT_THRESHOLD_MS) {
      snapshot.logResponsivenessSinceCreation(String.format(
        "Refresh session (queue size: %s, scanned: %s, result: %s, tries: %s, events: %d)",
        workQueue.size, types, if (myCancelled) "cancelled" else "done", count, events.size))
    }

    val result = if (events.isEmpty()) mutableListOf() else LinkedHashSet<VFileEvent>(events)
    myEventCount = result.size
    return result
  }

  override fun addEvents(events: List<VFileEvent>) {
    myEvents.addAll(events)
  }

  override fun cancel() {
    myCancelled = true

    val worker = myWorker
    worker?.cancel()
  }

  @RequiresEdt
  @RequiresWriteLock
  fun fireEvents(
    events: List<CompoundVFileEvent>,
    appliers: List<AsyncFileListener.ChangeApplier>,
    asyncProcessing: Boolean,
  ) {
    try {
      val app = ApplicationManagerEx.getApplicationEx()
      if ((myFinishRunnable != null || !events.isEmpty()) && !app.isDisposed()) {
        if (LOG.isDebugEnabled()) LOG.debug("events are about to fire: $events")
        app.runWriteActionWithNonCancellableProgressInDispatchThread(IdeCoreBundle.message("progress.title.file.system.synchronization"),
                                                                     null, null, Consumer { indicator: ProgressIndicator? ->
            indicator!!.setText(IdeCoreBundle.message("progress.text.processing.detected.file.changes", events.size))
            if (indicator is ProgressIndicatorWithDelayedPresentation) {
              indicator.setDelayInMillis(PROGRESS_THRESHOLD_MILLIS)
            }
           doFireEvents(events, appliers, asyncProcessing)
          })
      }
    }
    finally {
      mySemaphore.up()
    }
  }

  /**
   * Can work in both EDT and BGT
   */
  @RequiresWriteLock
  private fun fireEventsInWriteAction(
    events: List<CompoundVFileEvent>,
    appliers: List<AsyncFileListener.ChangeApplier>,
    asyncProcessing: Boolean,
  ) {
    val manager = VirtualFileManager.getInstance() as VirtualFileManagerImpl

    invokeOnEdt {
      manager.fireBeforeRefreshStart(this.isAsynchronous)
    }
    try {
      AsyncEventSupport.processEventsFromRefresh(events, appliers, asyncProcessing)
    }
    catch (e: AssertionError) {
      if (FileStatusMap.CHANGES_NOT_ALLOWED_DURING_HIGHLIGHTING == e.message) {
        throw AssertionError("VFS changes are not allowed during highlighting", myStartTrace)
      }
      throw e
    }
    finally {
      invokeOnEdt {
        try {
          manager.fireAfterRefreshFinish(this.isAsynchronous)
        }
        finally {
          myFinishRunnable?.run()
        }
      }
    }
  }

  private fun invokeOnEdt(r: Runnable) {
    if (EDT.isCurrentThreadEdt()) {
      r.run()
    }
    else {
      InternalThreading.invokeAndWaitWithTransferredWriteAction(r)
    }
  }


  @RequiresWriteLock
  @RequiresBackgroundThread
  fun fireEventsInBackgroundWriteAction(
    events: List<CompoundVFileEvent>,
    appliers: List<AsyncFileListener.ChangeApplier>,
  ) {
    try {
      val app = ApplicationManagerEx.getApplicationEx()
      if ((myFinishRunnable != null || !events.isEmpty()) && !app.isDisposed()) {
        if (LOG.isDebugEnabled()) LOG.debug("events are about to fire: " + events)
        withWriteActionTitle(IdeCoreBundle.message("progress.title.file.system.synchronization"), {
          doFireEvents(events, appliers, true)
        })
      }
    }
    finally {
      mySemaphore.up()
    }
  }

  @RequiresWriteLock
  private fun doFireEvents(events: List<CompoundVFileEvent>, appliers: List<AsyncFileListener.ChangeApplier>, asyncProcessing: Boolean) {
    var t = System.nanoTime()
    fireEventsInWriteAction(events, appliers, asyncProcessing)
    t = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t)
    if (t > PROGRESS_THRESHOLD_MILLIS) {
      LOG.warn("Long VFS change processing (" + t + "ms, " + events.size + " events): " + StringUtil.trimLog(events.subList(0, min(events.size, 100)).toString(), 10000))
    }
  }

  fun waitFor() {
    mySemaphore.waitForMaybeCancellable()
  }

  fun metric(key: String): Any {
    if (key == "events") return myEventCount
    throw IllegalArgumentException()
  }

  override fun toString(): String {
    return "RefreshSessionImpl: canceled=" + myCancelled + " launched=" + myLaunched + " queue=" + myWorkQueue.size + " events=" + myEventCount
  }

  companion object {
    private val LOG = Logger.getInstance(RefreshSession::class.java)

    private val RETRY_LIMIT = SystemProperties.getIntProperty("refresh.session.retry.limit", 3)
    private val DURATION_REPORT_THRESHOLD_MS = SystemProperties.getIntProperty("refresh.session.duration.report.threshold.seconds", -1) * 1000L
    private const val PROGRESS_THRESHOLD_MILLIS = 5000

    private fun getSaneModalityState(state: ModalityState): ModalityState {
      return if (state !== ModalityState.any()) state else ModalityState.nonModal()
    }
  }
}
