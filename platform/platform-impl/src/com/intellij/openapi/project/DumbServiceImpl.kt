// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.AccessToken
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.PingProgress
import com.intellij.openapi.project.MergingQueueGuiExecutor.ExecutorStateListener
import com.intellij.openapi.project.MergingTaskQueue.SubmissionReceipt
import com.intellij.openapi.project.SingleTaskExecutor.AutoclosableProgressive
import com.intellij.openapi.startup.StartupActivity.RequiredForSmartMode
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.ex.StatusBarEx
import com.intellij.serviceContainer.NonInjectable
import com.intellij.util.ConcurrencyUtil
import com.intellij.util.SystemProperties
import com.intellij.util.application
import com.intellij.util.indexing.IndexingBundle
import com.intellij.util.ui.DeprecationStripePanel
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Async
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.LockSupport
import javax.swing.JComponent

@ApiStatus.Internal
open class DumbServiceImpl @NonInjectable @VisibleForTesting constructor(private val myProject: Project,
                                                                         publisher: DumbModeListener) : DumbService(), Disposable, ModificationTracker, DumbServiceBalloon.Service {
  private val myState: AtomicReference<State>

  override val project: Project = myProject
  override var isAlternativeResolveEnabled: Boolean
    get() = myAlternativeResolveTracker.isAlternativeResolveEnabled
    set(enabled) {
      myAlternativeResolveTracker.isAlternativeResolveEnabled = enabled
    }

  override val modificationTracker: ModificationTracker = this

  @Volatile
  var dumbModeStartTrace: Throwable? = null
    private set
  private val myPublisher: DumbModeListener
  private var myModificationCount: Long = 0
  private val myCancellableLaterEdtInvoker: CancellableLaterEdtInvoker = CancellableLaterEdtInvoker(myProject)
  private val myTaskQueue: DumbServiceMergingTaskQueue = DumbServiceMergingTaskQueue()
  private val myGuiDumbTaskRunner: DumbServiceGuiExecutor
  private val mySyncDumbTaskRunner: DumbServiceSyncTaskQueue
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
      LOG.assertTrue(myState.get() == State.DUMB,
                     "State should be DUMB, but was " + myState.get())
      return true
    }

    override fun afterLastTask(latestReceipt: SubmissionReceipt?) {
      // If modality is null, then there is no dumb mode already (e.g. because the queue was processed under modal progress indicator)
      // There are no races: if DumbStartModality is about to change in myCancellableLaterEdtInvoker, we either observe:
      //   - if null modality about to change to non-null (happens only on EDT). This means that myLatestReceipt will also increase (on EDT)
      //   - if non-null modality about to change to null (happens only on EDT) then myState will also change to SMART (on EDT)
      // Either way, we don't need to execute the runnable. In the first case it will not be scheduled,
      // in the second case enterSmartModeIfDumb will do nothing.
      myCancellableLaterEdtInvoker.invokeLaterWithDumbStartModality {

        // Note that dumb service may already have been set to SMART state by completeJustSubmittedTasks.
        if (myLatestReceipt == latestReceipt) {
          enterSmartModeIfDumb()
        }
        // latestReceipt may be null if the queue is suspended or internal error has happened
        LOG.assertTrue(latestReceipt == null || !latestReceipt.isAfter(myLatestReceipt!!),
                       "latestReceipt=$latestReceipt must not be newer than the latest known myLatestReceipt=$myLatestReceipt")
      }
    }
  }

  constructor(project: Project) : this(project, project.messageBus.syncPublisher<DumbModeListener>(DUMB_MODE))

  init {
    myGuiDumbTaskRunner = DumbServiceGuiExecutor(myProject, myTaskQueue, DumbTaskListener())
    mySyncDumbTaskRunner = DumbServiceSyncTaskQueue(myProject, myTaskQueue)
    myPublisher = publisher
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
    myState = AtomicReference(if (myProject.isDefault) State.SMART else State.DUMB)

    // the first dumb mode should end in non-modal context
    myCancellableLaterEdtInvoker.setDumbStartModality(ModalityState.NON_MODAL)
  }

  fun queueStartupActivitiesRequiredForSmartMode() {
    queueTask(InitialDumbTaskRequiredForSmartMode(project))
    if (isSynchronousTaskExecution) {
      // This is the same side effects as produced by enterSmartModeIfDumb (except updating icons). We apply them synchronously, because
      // invokeLaterWithDumbStartModality(this::enterSmartModeIfDumb) does not work well in synchronous environments (e.g. in unit tests):
      // code continues to execute without waiting for smart mode to start because of invoke*Later*. See, for example, DbSrcFileDialectTest
      myState.compareAndSet(State.DUMB, State.SMART)
      myCancellableLaterEdtInvoker.invokeLaterWithDumbStartModality { myPublisher.exitDumbMode() }
    }
  }

  override fun cancelTask(task: DumbModeTask) {
    if (ApplicationManager.getApplication().isInternal) LOG.info(
      "cancel $task")
    myTaskQueue.cancelTask(task)
  }

  override fun dispose() {
    ApplicationManager.getApplication().assertWriteIntentLockAcquired()
    myBalloon.dispose()
    myCancellableLaterEdtInvoker.cancelAllPendingTasks()
    myTaskQueue.disposePendingTasks()
  }

  override fun suspendIndexingAndRun(activityName: @NlsContexts.ProgressText String, activity: Runnable) {
    myGuiDumbTaskRunner.suspendAndRun(activityName, activity)
  }

  override suspend fun suspendIndexingAndRun(activityName: @NlsContexts.ProgressText String, activity: suspend () -> Unit) {
    myGuiDumbTaskRunner.guiSuspender().suspendAndRun(activityName, activity)
  }

  override var isDumb: Boolean
    get() {
      if (ALWAYS_SMART) return false
      if (!ApplicationManager.getApplication().isReadAccessAllowed && Registry.`is`("ide.check.is.dumb.contract")) {
        LOG.error("To avoid race conditions isDumb method should be used only under read action or in EDT thread.",
                  IllegalStateException())
      }
      return myState.get() == State.DUMB
    }
    @TestOnly set(dumb) {
      ApplicationManager.getApplication().assertIsDispatchThread()
      if (dumb) {
        myState.set(State.DUMB)
        myPublisher.enteredDumbMode()
      }
      else {
        enterSmartModeIfDumb()
      }
    }

  @TestOnly
  fun runInDumbMode(runnable: Runnable) {
    isDumb = true
    try {
      runnable.run()
    }
    finally {
      isDumb = false
    }
  }

  override fun runWhenSmart(@Async.Schedule runnable: Runnable) {
    myProject.getService(SmartModeScheduler::class.java).runWhenSmart(runnable)
  }

  override fun unsafeRunWhenSmart(@Async.Schedule runnable: Runnable) {
    // we probably don't need unsafeRunWhenSmart anymore
    runWhenSmart(runnable)
  }

  override fun queueTask(task: DumbModeTask) {
    if (LOG.isDebugEnabled) {
      LOG.debug("Scheduling task $task")
    }
    if (myProject.isDefault) {
      LOG.error("No indexing tasks should be created for default project: $task")
    }
    if (isSynchronousTaskExecution) {
      mySyncDumbTaskRunner.runTaskSynchronously(task)
      return
    }
    val trace = Throwable()
    val modality = ModalityState.defaultModalityState()
    // we need EDT because enterDumbMode runs write action to make sure that we do not enter dumb mode during read actions
    val runnable = Runnable { queueTaskOnEdt(task, modality, trace) }
    if (ApplicationManager.getApplication().isDispatchThread) {
      runnable.run() // will log errors if not already in a write-safe context
    }
    else {
      myCancellableLaterEdtInvoker.invokeLaterWithDefaultModality(runnable) { Disposer.dispose(task) }
    }
  }

  private fun queueTaskOnEdt(task: DumbModeTask, modality: ModalityState, trace: Throwable) {
    myLatestReceipt = myTaskQueue.addTask(task)
    enterDumbModeIfSmart(modality, trace)

    // we want to invoke LATER. I.e. right now one can invoke completeJustSubmittedTasks and
    // drain the queue synchronously under modal progress
    myCancellableLaterEdtInvoker.invokeLaterWithDumbStartModality {
      try {
        myGuiDumbTaskRunner.startBackgroundProcess()
      }
      catch (t: Throwable) {
        // There are no evidences that returning to smart mode is a good strategy. Let it be like this until the opposite is needed.
        LOG.error("Failed to start background queue processing. Return to smart mode even though some tasks are still in the queue", t)
        enterSmartModeIfDumb()
      }
    }
  }

  private fun enterDumbModeIfSmart(modality: ModalityState, trace: Throwable) {
    application.assertWriteIntentLockAcquired()
    if (myState.get() == State.DUMB) return // don't event start unneeded write action

    val entered = WriteAction.compute<Boolean, RuntimeException> {
      if (!myState.compareAndSet(State.SMART, State.DUMB)) {
        return@compute false
      }
      dumbModeStartTrace = trace
      myCancellableLaterEdtInvoker.setDumbStartModality(modality)
      myModificationCount++
      !myProject.isDisposed
    }
    if (entered) {
      if (ApplicationManager.getApplication().isInternal) LOG.info("entered dumb mode")
      runCatching(Runnable { myPublisher.enteredDumbMode() })
    }
  }

  private fun enterSmartModeIfDumb() {
    application.assertWriteIntentLockAcquired()
    if (myState.get() == State.SMART) return // don't event start unneeded write action

    val entered = WriteAction.compute<Boolean, RuntimeException> {
      if (!myState.compareAndSet(State.DUMB, State.SMART)) {
        return@compute false
      }
      dumbModeStartTrace = null
      myModificationCount++
      myCancellableLaterEdtInvoker.setDumbStartModality(null)
      !myProject.isDisposed
    }
    if (entered) {
      if (ApplicationManager.getApplication().isInternal) LOG.info("entered smart mode")
      runCatching(Runnable { myPublisher.exitDumbMode() })
    }
  }

  override fun showDumbModeNotification(message: @NlsContexts.PopupContent String) {
    UIUtil.invokeLaterIfNeeded {
      val ideFrame = WindowManager.getInstance().getIdeFrame(myProject)
      if (ideFrame != null) {
        val statusBar = ideFrame.statusBar as StatusBarEx?
        statusBar?.notifyProgressByBalloon(MessageType.WARNING, message)
      }
    }
  }

  override fun showDumbModeActionBalloon(balloonText: @NlsContexts.PopupContent String,
                                         runWhenSmartAndBalloonStillShowing: Runnable) {
    myBalloon.showDumbModeActionBalloon(balloonText, runWhenSmartAndBalloonStillShowing)
  }

  override fun cancelAllTasksAndWait() {
    val application = ApplicationManager.getApplication()
    if (!application.isWriteIntentLockAcquired || application.isWriteAccessAllowed) {
      throw AssertionError("Must be called on write thread without write action")
    }
    LOG.info("Purge dumb task queue")
    val currentThread = Thread.currentThread()
    val initialThreadName = currentThread.name
    ConcurrencyUtil.runUnderThreadName(initialThreadName + " [DumbService.cancelAllTasksAndWait(state = " + myState.get() + ")]") {

      // isRunning will be false eventually, because we are on EDT, and no new task can be queued outside the EDT
      // (we only wait for currently running task to terminate).
      myGuiDumbTaskRunner.cancelAllTasks()
      while (myGuiDumbTaskRunner.isRunning.value && !myProject.isDisposed) {
        PingProgress.interactWithEdtProgress()
        LockSupport.parkNanos(50000000)
      }

      // Invoked after myGuiDumbTaskRunner has stopped to make sure that all the tasks submitted from the executor callbacks are canceled
      // This also cancels all the tasks that are waiting for the EDT to queue new dumb tasks
      myCancellableLaterEdtInvoker.cancelAllPendingTasks()
    }
  }

  override fun waitForSmartMode() {
    waitForSmartMode(Long.MAX_VALUE)
  }

  fun waitForSmartMode(milliseconds: Long): Boolean {
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
      if (startTime + milliseconds < System.currentTimeMillis()) return false
    }
    return true
  }

  override fun wrapGently(dumbUnawareContent: JComponent, parentDisposable: Disposable): JComponent {
    val wrapper = DumbUnawareHider(dumbUnawareContent)
    wrapper.setContentVisible(!isDumb)
    project.messageBus.connect(parentDisposable).subscribe<DumbModeListener>(DUMB_MODE, object : DumbModeListener {
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
    project.messageBus.connect(parentDisposable).subscribe<DumbModeListener>(DUMB_MODE, object : DumbModeListener {
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
                                                      if (isDumb) {
                                                        runWhenSmart { smartInvokeLater(runnable, modalityState) }
                                                      }
                                                      else {
                                                        runnable.run()
                                                      }
                                                    }, modalityState, myProject.disposed)
  }

  override fun completeJustSubmittedTasks() {
    ApplicationManager.getApplication().assertWriteIntentLockAcquired()
    LOG.assertTrue(myProject.isInitialized, "Project should have been initialized")
    while (myState.get() == State.DUMB) {
      val queueProcessedUnderModalProgress = processQueueUnderModalProgress()
      if (!queueProcessedUnderModalProgress) {
        // processQueueUnderModalProgress did nothing (i.e. processing is being done under non-modal indicator)
        break
      }
    }
    if (myState.get() == State.SMART) { // we can reach this statement in dumb mode if queue is processed in background
      // DumbServiceSyncTaskQueue does not respect threading policies: it can add tasks outside of EDT
      // and process them without switching to dumb mode. This behavior has to be fixed, but for now just ignore
      // it, because it has been working like this for years already.
      // Reproducing in test: com.jetbrains.cidr.lang.refactoring.OCRenameMoveFileTest
      LOG.assertTrue(isSynchronousTaskExecution || myTaskQueue.isEmpty, "Task queue is not empty. Current state is " + myState.get())
    }
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
        if (myTaskQueue.isEmpty) {
          enterSmartModeIfDumb()
        }
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
    return myModificationCount
  }

  private enum class State {
    SMART,
    DUMB
  }

  companion object {
    @JvmField
    val REQUIRED_FOR_SMART_MODE_STARTUP_ACTIVITY = ExtensionPointName<RequiredForSmartMode>(
      "com.intellij.requiredForSmartModeStartupActivity")

    @JvmField
    val ALWAYS_SMART = SystemProperties.getBooleanProperty("idea.no.dumb.mode", false)

    private val LOG = Logger.getInstance(DumbServiceImpl::class.java)

    @JvmStatic
    fun getInstance(project: Project): DumbServiceImpl {
      return DumbService.getInstance(project) as DumbServiceImpl
    }

    private fun runCatching(runnable: Runnable) {
      try {
        runnable.run()
      }
      catch (pce: ProcessCanceledException) {
        throw pce
      }
      catch (e: Exception) {
        LOG.error(e)
      }
    }

    @JvmStatic
    val isSynchronousTaskExecution: Boolean
      get() {
        val application = ApplicationManager.getApplication()
        return (application.isUnitTestMode || isSynchronousHeadlessApplication) &&
               !java.lang.Boolean.parseBoolean(System.getProperty(IDEA_FORCE_DUMB_QUEUE_TASKS, "false"))
      }

    const val IDEA_FORCE_DUMB_QUEUE_TASKS = "idea.force.dumb.queue.tasks"

    private val isSynchronousHeadlessApplication: Boolean
      get() = application.isHeadlessEnvironment && !java.lang.Boolean.getBoolean("ide.async.headless.mode")
  }
}
