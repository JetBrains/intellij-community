// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.*
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.progress.util.PingProgress
import com.intellij.openapi.project.MergingQueueGuiExecutor.ExecutorStateListener
import com.intellij.openapi.project.MergingTaskQueue.SubmissionReceipt
import com.intellij.openapi.project.SingleTaskExecutor.AutoclosableProgressive
import com.intellij.openapi.startup.StartupActivity.RequiredForSmartMode
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.ex.StatusBarEx
import com.intellij.platform.util.coroutines.childScope
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.serviceContainer.NonInjectable
import com.intellij.util.ConcurrencyUtil
import com.intellij.util.SystemProperties
import com.intellij.util.application
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.indexing.IndexingBundle
import com.intellij.util.ui.DeprecationStripePanel
import com.intellij.util.ui.EDT
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.*
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.LockSupport
import java.util.function.BooleanSupplier
import javax.swing.JComponent

@ApiStatus.Internal
open class DumbServiceImpl @NonInjectable @VisibleForTesting constructor(private val myProject: Project,
                                                                         private val myPublisher: DumbModeListener,
                                                                         private val scope: CoroutineScope) : DumbService(), Disposable, ModificationTracker, DumbServiceBalloon.Service {
  private val myState: MutableStateFlow<DumbState> = MutableStateFlow(DumbState(!myProject.isDefault, 0L, 0))

  private val initialDumbTaskRequiredForSmartModeSubmitted = AtomicBoolean(false)

  // should only be accessed from EDT. This is to order synchronous and asynchronous publishing
  private var lastPublishedState: DumbState = myState.value

  @Volatile
  private var isDisposed = false

  // Not thread safe. Should only be accessed from EDT. Launches myGuiDumbTaskRunner at most once.
  // DumbService can invoke `launch` from completeJustSubmittedTasks or from queueTaskOnEdt
  private inner class DumbTaskLauncher(private val modality: ModalityState) {
    private var launched = false

    private val closed = AtomicBoolean(false)

    @Volatile
    private var closeTrace: Throwable? = null // for diagnostics

    fun cancel() {
      // only not launched tasks can be canceled
      if (!launched) {
        launched = true
        close()
      }
    }

    private fun close() {
      if (closed.compareAndSet(false, true)) {
        closeTrace = Throwable("Close trace")
        if (application.isDispatchThread) {
          dumbTaskLaunchers.remove(this)
          // without redispatching, because it can be invoked from completeJustSubmittedTasks
          decrementDumbCounter()
        }
        else {
          scope.launch(modality.asContextElement() + Dispatchers.EDT) {
            writeIntentReadAction {
              dumbTaskLaunchers.remove(this@DumbTaskLauncher)
              decrementDumbCounter()
            }
          }
        }
      }
      else {
        LOG.error("The task is already closed", Throwable("Current trace", closeTrace))
      }
    }

    fun launch() {
      if (!launched) {
        launched = true;
        myGuiDumbTaskRunner.startBackgroundProcess(onFinish = {
          close()
        })
      }
    }
  }

  // should only be accessed from EDT. We need to track FutureDumbTasks because completeJustSubmittedTasks should
  // not only complete all the dumb tasks, but also should finish dumb mode.
  private val dumbTaskLaunchers: MutableList<DumbTaskLauncher> = ArrayList()

  override val project: Project = myProject
  override var isAlternativeResolveEnabled: Boolean
    get() = myAlternativeResolveTracker.isAlternativeResolveEnabled
    set(enabled) {
      myAlternativeResolveTracker.isAlternativeResolveEnabled = enabled
    }

  override val modificationTracker: ModificationTracker get() = this

  @Volatile
  var dumbModeStartTrace: Throwable? = null
    private set
  private var scheduledTasksScope: CoroutineScope = scope.childScope()
  private val myTaskQueue: DumbServiceMergingTaskQueue = DumbServiceMergingTaskQueue()
  private val myGuiDumbTaskRunner: DumbServiceGuiExecutor
  private val myAlternativeResolveTracker: DumbServiceAlternativeResolveTracker

  //used from EDT
  private val myBalloon: DumbServiceBalloon

  @Volatile
  private var myWaitIntolerantThread: Thread? = null

  // should only be accessed from EDT to avoid races between `queueTaskOnEDT` and `enterSmartModeIfDumb` (invoked from `afterLastTask`)
  private var myLatestReceipt: SubmissionReceipt? = null

  private inner class DumbTaskListener : ExecutorStateListener {
    /*
     * beforeFirstTask and afterLastTask always follow one after another. Receiving several beforeFirstTask or afterLastTask in row is
     * always a failure of DumbServiceGuiTaskQueue.
     * return true to start queue processing, false otherwise
     */
    override fun beforeFirstTask(): Boolean {
      // if a queue has already been emptied by modal dumb progress, DumbServiceGuiExecutor will not invoke processing on empty queue
      LOG.assertTrue(myState.value.isDumb,
                     "State should be DUMB, but was " + myState.value)
      return true
    }

    override fun afterLastTask(latestReceipt: SubmissionReceipt?) {

    }
  }

  constructor(project: Project, scope: CoroutineScope) : this(project, project.messageBus.syncPublisher<DumbModeListener>(DUMB_MODE), scope)

  init {
    myGuiDumbTaskRunner = DumbServiceGuiExecutor(myProject, myTaskQueue, DumbTaskListener())
    if (Registry.`is`("scanning.should.pause.dumb.queue", false)) {
      myProject.service<DumbServiceScanningListener>().subscribe()
    }
    if (Registry.`is`("vfs.refresh.should.pause.dumb.queue", true)) {
      DumbServiceVfsBatchListener(myProject, myGuiDumbTaskRunner.guiSuspender())
    }
    myBalloon = DumbServiceBalloon(myProject, this)
    myAlternativeResolveTracker = DumbServiceAlternativeResolveTracker()
    // any project starts in dumb mode (except default project which is always smart)
    // we assume that queueStartupActivitiesRequiredForSmartMode will be invoked to advance DUMB > SMART
  }

  internal suspend fun queueStartupActivitiesRequiredForSmartMode() {
    if (!initialDumbTaskRequiredForSmartModeSubmitted.compareAndSet(false, true)) {
      return
    }

    val task = InitialDumbTaskRequiredForSmartMode(project)
    blockingContext {
      queueTask(task)
    }
  }

  override fun cancelTask(task: DumbModeTask) {
    LOG.info("cancel $task [${project.name}]")
    myTaskQueue.cancelTask(task)
  }

  override fun dispose() {
    isDisposed = true
    ApplicationManager.getApplication().assertWriteIntentLockAcquired()
    myBalloon.dispose()
    scheduledTasksScope.cancel("On dispose of DumbService", ProcessCanceledException())
    myTaskQueue.disposePendingTasks()
  }

  override fun suspendIndexingAndRun(activityName: @NlsContexts.ProgressText String, activity: Runnable) {
    myGuiDumbTaskRunner.suspendAndRun(activityName, activity)
  }

  override suspend fun suspendIndexingAndRun(activityName: @NlsContexts.ProgressText String, activity: suspend () -> Unit) {
    myGuiDumbTaskRunner.guiSuspender().suspendAndRun(activityName, activity)
  }

  override val isDumb: Boolean
    get() {
      if (ALWAYS_SMART) return false
      if (!ApplicationManager.getApplication().isReadAccessAllowed && Registry.`is`("ide.check.is.dumb.contract")) {
        LOG.error("To avoid race conditions isDumb method should be used only under read action or in EDT thread.",
                  IllegalStateException())
      }
      return myState.value.isDumb
    }

  /**
   * Same as [isDumb], but if form of a flow with the following properties:
   *   * This flow never completes
   *   * Last emitted value is always immediately available
   *   * Conflation and other properties of the flow are not specified
   *
   * In most cases you don't need this method, because neither checking `isDumb` outside a read action, nor suspending inside a read action
   *  have little sense except a few very special use cases.
   */
  val isDumbAsFlow: Flow<Boolean> = myState.map { it.isDumb }

  /**
   * This method starts dumb mode (if not started), then runs suspend lambda, then ends dumb mode (if no other dumb tasks are running).
   *
   * This method can be invoked from any thread. It will switch to EDT to start/stop dumb mode. Runnable itself will be invoked from
   * method's invocation context (thread).
   */
  @TestOnly
  suspend fun <T> runInDumbMode(block: suspend () -> T): T = runInDumbMode("test", block)

  /**
   * This method starts dumb mode (if not started), then runs suspend lambda, then ends dumb mode (if no other dumb tasks are running).
   *
   * This method can be invoked from any thread. It will switch to EDT to start/stop dumb mode. Runnable itself will be invoked from
   * method's invocation context (thread).
   *
   * @param debugReason will only be printed to logs
   */
  @Internal
  suspend fun <T> runInDumbMode(debugReason: @NonNls String, block: suspend () -> T): T {
    LOG.info("[$project]: running dumb task without visible indicator: $debugReason")

    suspend fun incrementCounter() {
      blockingContext { // because we need correct modality
        // Because we need to avoid additional dispatch. UNDISPATCHED coroutine is not a solution, because
        // multiple UNDISPATCHED coroutines in the same (EDT) thread ends up in some strange state (as revealed by unit tests)
        incrementDumbCounter(trace = Throwable())
      }
    }

    if (EDT.isCurrentThreadEdt()) {
      incrementCounter()
    }
    else {
      withContext(Dispatchers.EDT) {
        incrementCounter()
      }
    }

    try {
      return block()
    }
    finally {
      // in the case of cancellation this block won't execute if NonCancellable is omitted
      withContext(Dispatchers.EDT + NonCancellable) {
        blockingContext {
          decrementDumbCounter()
        }
        LOG.info("[$project]: finished dumb task without visible indicator: $debugReason")
      }
    }
  }

  // We cannot make this function `suspend`, because we have a contract that if dumb task is queued from EDT, dumb service becomes dumb
  // immediately. DumbService.queue is blocking method at the moment.
  @RequiresBlockingContext
  private fun incrementDumbCounter(trace: Throwable) {
    ThreadingAssertions.assertEventDispatchThread()
    if (myState.getAndUpdate { it.tryIncrementDumbCounter() }.incrementWillChangeDumbState) {
      // If already dumb - just increment the counter. We don't need a write action (to not interrupt NBRA), neither we need EDT.
      // Otherwise, increment the counter under write action because this will change dumb state
      val enteredDumb = application.runWriteAction(Computable {
        val old = myState.getAndUpdate { it.incrementDumbCounter() }
        return@Computable old.isSmart
      })
      if (enteredDumb) {
        LOG.info("enter dumb mode [${project.name}]")
        dumbModeStartTrace = trace
        try {
          publishDumbModeChangedEvent()
        }
        catch (t: Throwable) {
          // in unit tests we may get here because of exception thrown from Log.error from catch block inside runCatchingIgnorePCE
          decrementDumbCounter()
          throw t
        }
      }
    }

    LOG.assertTrue(myState.value.isDumb, "Should be dumb")
  }

  // this method is not suspend for the sake of symmetry: incrementDumbCounter is not suspend as of now
  @RequiresBlockingContext
  private fun decrementDumbCounter() {
    ThreadingAssertions.assertEventDispatchThread()

    // If there are other dumb tasks - just decrement the counter. We don't need a write action (to not interrupt NBRA), neither we need EDT.
    // Otherwise, decrement the counter under write action because this will change dumb state
    if (myState.getAndUpdate { it.tryDecrementDumbCounter() }.decrementWillChangeDumbState) {
      val exitDumb = application.runWriteAction(Computable {
        val new = myState.updateAndGet { it.decrementDumbCounter() }
        return@Computable new.isSmart
      })
      if (exitDumb) {
        LOG.info("exit dumb mode [${project.name}]")
        dumbModeStartTrace = null
        publishDumbModeChangedEvent()
      }
    }
  }

  private fun publishDumbModeChangedEvent() {
    ThreadingAssertions.assertEventDispatchThread()
    val currentState = myState.value
    if (lastPublishedState.modificationCounter >= currentState.modificationCounter) {
      return // already published
    }

    // First change lastPublishedState, then publish. This is to address the situation that new event
    // should be published while publishing current event
    val wasDumb = lastPublishedState.isDumb
    lastPublishedState = myState.value

    if (wasDumb != currentState.isDumb) {
      if (currentState.isDumb) {
        runCatchingIgnorePCE { WriteIntentReadAction.run { myPublisher.enteredDumbMode() } }
      }
      else {
        runCatchingIgnorePCE { WriteIntentReadAction.run { myPublisher.exitDumbMode() } }
      }
    }
  }

  override fun runWhenSmart(@Async.Schedule runnable: Runnable) {
    myProject.getService(SmartModeScheduler::class.java).runWhenSmart(runnable)
  }

  internal val runWhenSmartCondition: BooleanSupplier
    get() = myProject.getService(SmartModeScheduler::class.java).runWhenSmartCondition

  override fun unsafeRunWhenSmart(@Async.Schedule runnable: Runnable) {
    // we probably don't need unsafeRunWhenSmart anymore
    runWhenSmart(runnable)
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun queueTask(task: DumbModeTask) {
    if (isDisposed) {
      throw AlreadyDisposedException("Cannot queue task $task after disposal")
    }

    LOG.debug { "Scheduling task $task" }
    if (myProject.isDefault) {
      LOG.error("No indexing tasks should be created for default project: $task")
    }
    val trace = Throwable()
    var modality = ModalityState.defaultModalityState()
    if (modality == ModalityState.any()) {
      LOG.error("Unexpected modality: should not be ANY. Replace with NON_MODAL")
      modality = ModalityState.nonModal()
    }
    if (ApplicationManager.getApplication().isDispatchThread) {
      queueTaskOnEdt(task, modality, trace)
    }
    else {
      invokeLaterOnEdtInScheduledTasksScope(start = CoroutineStart.ATOMIC) {
        try {
          ensureActive()
          queueTaskOnEdt(task, modality, trace)
        }
        catch (t: CancellationException) {
          Disposer.dispose(task)
        }
      }
    }
  }

  private fun invokeLaterOnEdtInScheduledTasksScope(start: CoroutineStart = CoroutineStart.DEFAULT,
                                                    block: suspend CoroutineScope.() -> Unit): Job {
    val modality = ModalityState.defaultModalityState()
    return scheduledTasksScope.launch(modality.asContextElement() + Dispatchers.EDT, start, block)
  }

  private fun queueTaskOnEdt(task: DumbModeTask, modality: ModalityState, trace: Throwable) {
    // first, increment dumb mode, then add the task. If increment failed, task execution will not be scheduled, and we will stuck in
    // dumb mode. In unit tests, much safer behavior is to ignore the task. In prod both behaviors are bad.
    incrementDumbCounter(trace)

    ThreadingAssertions.assertEventDispatchThread()
    myLatestReceipt = myTaskQueue.addTask(task)

    // we want to invoke LATER. I.e. right now one can invoke completeJustSubmittedTasks and
    // drain the queue synchronously under modal progress
    var dumbModeCounterWillBeDecrementedFromOnFinish = false
    val launcher = DumbTaskLauncher(modality)
    dumbTaskLaunchers.add(launcher)
    invokeLaterOnEdtInScheduledTasksScope {
      dumbModeCounterWillBeDecrementedFromOnFinish = true
      launcher.launch()
    }.invokeOnCompletion {
      if (!dumbModeCounterWillBeDecrementedFromOnFinish) {
        scope.launch(modality.asContextElement() + Dispatchers.EDT) {
          blockingContext {
            launcher.cancel()
          }
        }
      }
    }
  }

  override fun showDumbModeNotification(message: @NlsContexts.PopupContent String) {
    showDumbModeNotificationForFunctionality(message, DumbModeBlockedFunctionality.Other)
  }

  override fun showDumbModeNotificationForFunctionality(message: @NlsContexts.PopupContent String,
                                                        functionality: DumbModeBlockedFunctionality) {
    DumbModeBlockedFunctionalityCollector.logFunctionalityBlocked(project, functionality)
    doShowDumbModeNotification(message)
  }

  /**
   * Doesn't log new event if the equality object is equal to the previous one
   */
  fun showDumbModeNotificationForFunctionalityWithCoalescing(message: @NlsContexts.PopupContent String,
                                                             functionality: DumbModeBlockedFunctionality,
                                                             equality: Any) {
    DumbModeBlockedFunctionalityCollector.logFunctionalityBlockedWithCoalescing(project, functionality, equality)
    doShowDumbModeNotification(message)
  }

  private fun doShowDumbModeNotification(message: @NlsContexts.PopupContent String) {
    UIUtil.invokeLaterIfNeeded {
      val ideFrame = WindowManager.getInstance().getIdeFrame(myProject)
      if (ideFrame != null) {
        val statusBar = ideFrame.statusBar as StatusBarEx?
        statusBar?.notifyProgressByBalloon(MessageType.WARNING, message)
      }
    }
  }

  override fun showDumbModeNotificationForAction(message: @NlsContexts.PopupContent String, actionId: String?) {
    if (actionId == null) {
      DumbModeBlockedFunctionalityCollector.logFunctionalityBlocked(project, DumbModeBlockedFunctionality.ActionWithoutId)
    }
    else {
      DumbModeBlockedFunctionalityCollector.logActionBlocked(project, actionId)
    }
    doShowDumbModeNotification(message)
  }

  override fun showDumbModeActionBalloon(balloonText: @NlsContexts.PopupContent String,
                                         runWhenSmartAndBalloonStillShowing: Runnable,
                                         actionIds: List<String>) {
    myBalloon.showDumbModeActionBalloon(balloonText, {
      DumbModeBlockedFunctionalityCollector.logActionsBlocked(project, actionIds, true)
      runWhenSmartAndBalloonStillShowing.run()
    }, { DumbModeBlockedFunctionalityCollector.logActionsBlocked(project, actionIds, false) })
  }

  override fun cancelAllTasksAndWait() {
    val application = ApplicationManager.getApplication()
    if (!application.isWriteIntentLockAcquired || application.isWriteAccessAllowed) {
      throw AssertionError("Must be called on write thread without write action")
    }
    LOG.info("Purge dumb task queue")
    val currentThread = Thread.currentThread()
    val initialThreadName = currentThread.name
    ConcurrencyUtil.runUnderThreadName(initialThreadName + " [DumbService.cancelAllTasksAndWait(state = " + myState.value + ")]") {

      // isRunning will be false eventually, because we are on EDT, and no new task can be queued outside the EDT
      // (we only wait for currently running task to terminate).
      myGuiDumbTaskRunner.cancelAllTasks()
      while (myGuiDumbTaskRunner.isRunning.value && !myProject.isDisposed) {
        PingProgress.interactWithEdtProgress()
        LockSupport.parkNanos(50000000)
      }

      // Invoked after myGuiDumbTaskRunner has stopped to make sure that all the tasks submitted from the executor callbacks are canceled
      // This also cancels all the tasks that are waiting for the EDT to queue new dumb tasks
      val oldTaskScope = scheduledTasksScope
      scheduledTasksScope = scope.childScope()
      oldTaskScope.cancel("DumbService.cancelAllTasksAndWait", ProcessCanceledException())
    }
  }

  override fun waitForSmartMode() {
    waitForSmartMode(null)
  }

  fun waitForSmartMode(milliseconds: Long?): Boolean {
    if (ALWAYS_SMART) return true
    val application = ApplicationManager.getApplication()
    if (application.isReadAccessAllowed) {
      throw AssertionError("Don't invoke waitForSmartMode from inside read action in dumb mode")
    }
    if (myWaitIntolerantThread === Thread.currentThread()) {
      throw AssertionError("Don't invoke waitForSmartMode from a background startup activity")
    }
    val switched = CountDownLatch(1)
    val smartModeScheduler = myProject.getService(SmartModeScheduler::class.java)
    smartModeScheduler.runWhenSmart { switched.countDown() }

    // we check getCurrentMode here because of tests which may hang because runWhenSmart needs EDT for scheduling
    val startTime = System.currentTimeMillis()
    while (!myProject.isDisposed && smartModeScheduler.getCurrentMode() != 0) {
      // it is fine to unblock the caller when myProject.isDisposed, even if didn't reach smart mode: we are on background thread
      // without read action. Dumb mode may start immediately after the caller is unblocked, so caller is prepared for this situation.
      try {
        if (switched.await(50, TimeUnit.MILLISECONDS)) break
      }
      catch (ignored: InterruptedException) {
      }
      ProgressManager.checkCanceled()
      if (milliseconds != null && startTime + milliseconds < System.currentTimeMillis()) return false
    }
    return true
  }

  override fun wrapGently(dumbUnawareContent: JComponent, parentDisposable: Disposable): JComponent {
    val wrapper = DumbUnawareHider(dumbUnawareContent)
    wrapper.setContentVisible(!isDumb)
    project.messageBus.connect(parentDisposable).subscribe(DUMB_MODE, object : DumbModeListener {
      override fun enteredDumbMode() {
        wrapper.setContentVisible(false)
      }

      override fun exitDumbMode() {
        wrapper.setContentVisible(true)
      }
    })
    return wrapper
  }

  override fun wrapWithSpoiler(dumbAwareContent: JComponent, updateRunnable: Runnable, parentDisposable: Disposable): JComponent {
    //TODO replace with a proper mockup implementation
    val stripePanel = DeprecationStripePanel(IdeBundle.message("dumb.mode.results.might.be.incomplete"), AllIcons.General.Warning)
      .withAlternativeAction(IdeBundle.message("dumb.mode.spoiler.wrapper.reload.text"), object : DumbAwareAction() {
        override fun actionPerformed(e: AnActionEvent) {
          updateRunnable.run()
        }
      })
    stripePanel.isVisible = isDumb
    project.messageBus.connect(parentDisposable).subscribe(DUMB_MODE, object : DumbModeListener {
      override fun enteredDumbMode() {
        stripePanel.isVisible = true
        updateRunnable.run()
      }

      override fun exitDumbMode() {
        stripePanel.isVisible = false
        updateRunnable.run()
      }
    })
    return stripePanel.wrap(dumbAwareContent)
  }

  override fun smartInvokeLater(runnable: Runnable) {
    smartInvokeLater(runnable, ModalityState.defaultModalityState())
  }

  override fun smartInvokeLater(runnable: Runnable, modalityState: ModalityState) {
    ApplicationManager.getApplication().invokeLater({
                                                      if (runWhenSmartCondition.asBoolean) {
                                                        runnable.run()
                                                      }
                                                      else {
                                                        LOG.debug("smartInvokeLater dispatched")
                                                        runWhenSmart { smartInvokeLater(runnable, modalityState) }
                                                      }
                                                    }, modalityState, myProject.disposed)
  }

  override fun completeJustSubmittedTasks() {
    ApplicationManager.getApplication().assertWriteIntentLockAcquired()
    LOG.assertTrue(myProject.isInitialized, "Project should have been initialized")

    // there is no race: myTaskQueue is only updated from EDT
    if (!myTaskQueue.isEmpty) {
      incrementDumbCounter(Throwable())
      try {
        while (!myTaskQueue.isEmpty) {
          val queueProcessedUnderModalProgress = processQueueUnderModalProgress()
          if (!queueProcessedUnderModalProgress) {
            if (application.isUnitTestMode) {
              LOG.assertTrue(myTaskQueue.isEmpty, "This behavior is valid, but most likely not expected in tests: " +
                                                  "completeJustSubmittedTasks does nothing because the queue is already " +
                                                  "being processed in the background thread.")
            }
            // processQueueUnderModalProgress did nothing (i.e. processing is being done under non-modal indicator)
            break
          }
        }
      }
      finally {
        decrementDumbCounter()
      }
    }

    // there is no race: dumbTaskLaunchers is only updated from EDT
    // the myTaskQueue is empty, we expect that DumbTaskLauncher::launch will do nothing other than finishing dumb mode
    ArrayList(dumbTaskLaunchers).forEach(DumbTaskLauncher::launch) // we need a copy, because the task will remove itself from the list

    // it is still possible that the IDE is dumb at this point. This may happen if dumb queue is actually processed
    // in the background, and the background thread is processing the last task from the queue.
    // This will happen, for example, in unit tests in DUMB_EMPTY_INDEX indexing mode: background thread will be processing
    // the eternal task.
  }

  private fun processQueueUnderModalProgress(): Boolean {
    val startTrace = Throwable()
    NoAccessDuringPsiEvents.checkCallContext("modal indexing")
    return myGuiDumbTaskRunner.tryStartProcessInThisThread { processTask: AutoclosableProgressive ->
      try {
        LOG.infoWithDebug("Processing dumb queue under modal progress (start)", startTrace)
        (ApplicationManager.getApplication() as ApplicationImpl).executeSuspendingWriteAction(myProject, IndexingBundle.message(
          "progress.indexing.title")) {
          processTask.use {
            processTask.run(
              ProgressManager.getInstance().progressIndicator)
          }
        }
      }
      finally {
        LOG.infoWithDebug("Processing dumb queue under modal progress (end)", startTrace)
      }
    }
  }

  override fun runWithWaitForSmartModeDisabled(): AccessToken {
    myWaitIntolerantThread = Thread.currentThread()
    return object : AccessToken() {
      override fun finish() {
        myWaitIntolerantThread = null
      }
    }
  }

  override fun getModificationCount(): Long {
    // todo: drop mod tracker in scanner executor after there is a proper way to track indexes updates IJPL-472
    return myState.value.modificationCounter + UnindexedFilesScannerExecutor.getInstance(project).modificationTracker.modificationCount
  }

  internal val dumbStateAsFlow: StateFlow<DumbState> = myState

  internal data class DumbState(
    val isDumb: Boolean, val modificationCounter: Long, val dumbCounter: Int
  ) {
    private fun nextCounterState(nextVal: Int): DumbState {
      if (nextVal > 0) {
        return DumbState(true, modificationCounter + 1, nextVal)
      }
      else {
        LOG.assertTrue(nextVal == 0) { "Invalid nextVal=$nextVal" }
        return DumbState(false, modificationCounter + 1, 0)
      }
    }

    val incrementWillChangeDumbState: Boolean = isSmart
    val decrementWillChangeDumbState: Boolean = dumbCounter == 1
    fun incrementDumbCounter(): DumbState = nextCounterState(dumbCounter + 1)
    fun decrementDumbCounter(): DumbState = nextCounterState(dumbCounter - 1)
    fun tryIncrementDumbCounter(): DumbState = if (incrementWillChangeDumbState) this else incrementDumbCounter()
    fun tryDecrementDumbCounter(): DumbState = if (decrementWillChangeDumbState) this else decrementDumbCounter()
    val isSmart: Boolean get() = !isDumb
  }

  @TestOnly
  fun ensureInitialDumbTaskRequiredForSmartModeSubmitted() {
    if (!initialDumbTaskRequiredForSmartModeSubmitted.get()) {
      runBlockingMaybeCancellable {
        queueStartupActivitiesRequiredForSmartMode()
      }
    }
  }

  @TestOnly
  fun isRunning(): Boolean {
    return myGuiDumbTaskRunner.isRunning.value
  }

  @TestOnly
  fun hasScheduledTasks(): Boolean {
    // when queued on EDT, dumb mode starts immediately, but executor does not start immediately - it schedules start to the end of the EDT
    // queue to give a chance to invoke completeJustSubmittedTasks and index files under modal progress.
    return scheduledTasksScope.coroutineContext.job.children.firstOrNull() != null
  }

  companion object {
    @JvmField
    val REQUIRED_FOR_SMART_MODE_STARTUP_ACTIVITY: ExtensionPointName<RequiredForSmartMode> = ExtensionPointName(
      "com.intellij.requiredForSmartModeStartupActivity")

    @JvmField
    val ALWAYS_SMART: Boolean = SystemProperties.getBooleanProperty("idea.no.dumb.mode", false)

    private val LOG = Logger.getInstance(DumbServiceImpl::class.java)

    @JvmStatic
    fun getInstance(project: Project): DumbServiceImpl {
      return DumbService.getInstance(project) as DumbServiceImpl
    }

    private fun runCatchingIgnorePCE(runnable: Runnable) {
      try {
        runnable.run()
      }
      catch (ignored: ProcessCanceledException) {
      }
      catch (t: Throwable) {
        LOG.error(t)
      }
    }

    @JvmStatic
    val isSynchronousTaskExecution: Boolean
      get() {
        val application = ApplicationManager.getApplication()
        return (application.isUnitTestMode || isSynchronousHeadlessApplication) &&
               !java.lang.Boolean.parseBoolean(System.getProperty(IDEA_FORCE_DUMB_QUEUE_TASKS, "false"))
      }

    /**
     * Flag to force dumb tasks to work on background thread in tests or synchronous headless mode.
     */
    const val IDEA_FORCE_DUMB_QUEUE_TASKS: String = "idea.force.dumb.queue.tasks"

    private val isSynchronousHeadlessApplication: Boolean
      get() = application.isHeadlessEnvironment && !java.lang.Boolean.getBoolean("ide.async.headless.mode")
  }
}
