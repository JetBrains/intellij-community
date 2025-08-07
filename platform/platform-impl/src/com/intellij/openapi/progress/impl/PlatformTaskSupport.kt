// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.impl

import com.intellij.codeWithMe.ClientId
import com.intellij.concurrency.resetThreadContext
import com.intellij.ide.IdeBundle
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.consumeUnrelatedEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.contextModality
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.impl.JobProvider
import com.intellij.openapi.application.impl.inModalContext
import com.intellij.openapi.application.isModalAwareContext
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.progress.*
import com.intellij.openapi.progress.util.ProgressDialogUI
import com.intellij.openapi.progress.util.ProgressIndicatorWithDelayedPresentation.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.openapi.progress.util.createDialogWrapper
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.impl.DialogWrapperPeerImpl.isHeadlessEnv
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.util.NlsContexts.ModalProgressTitle
import com.intellij.openapi.util.NlsContexts.ProgressTitle
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ex.IdeFrameEx
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.openapi.wm.impl.status.IdeStatusBarImpl
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.ide.progress.*
import com.intellij.platform.ide.progress.suspender.*
import com.intellij.platform.kernel.withKernel
import com.intellij.platform.util.coroutines.flow.throttle
import com.intellij.platform.util.progress.ProgressPipe
import com.intellij.platform.util.progress.ProgressState
import com.intellij.platform.util.progress.createProgressPipe
import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.util.application
import com.intellij.util.awaitCancellationAndInvoke
import com.intellij.util.ui.RawSwingDispatcher
import fleet.kernel.rete.collect
import fleet.kernel.rete.filter
import fleet.kernel.tryWithEntities
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import java.awt.*
import javax.swing.JFrame
import javax.swing.SwingUtilities
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.coroutineContext

internal val isRhizomeProgressEnabled
  get() = Registry.`is`("rhizome.progress")

internal val isRhizomeProgressModelEnabled
  get() = Registry.`is`("rhizome.progress.model")

private val LOG = logger<PlatformTaskSupport>()

@Internal
class PlatformTaskSupport(private val cs: CoroutineScope) : TaskSupport {
  data class ProgressStartedEvent(
    val title: @Nls String,
    val cancellation: TaskCancellation,
    val context: CoroutineContext,
    val updates: Flow<ProgressState>, // finite
  )

  private val _progressStarted: MutableSharedFlow<ProgressStartedEvent> = MutableSharedFlow()

  val progressStarted: SharedFlow<ProgressStartedEvent> = _progressStarted.asSharedFlow()

  private suspend fun progressStarted(title: @Nls String, cancellation: TaskCancellation, updates: Flow<ProgressState>) {
    val context = coroutineContext
    _progressStarted.emit(ProgressStartedEvent(title, cancellation, context, updates.finishWhenJobCompletes(context.job)))
  }

  private fun Flow<ProgressState>.finishWhenJobCompletes(job: Job): Flow<ProgressState> {
    val isActiveFlow = MutableStateFlow(true)
    job.invokeOnCompletion { isActiveFlow.value = false }
    val finiteFlow = combine(isActiveFlow) { state, isActive ->
      state.takeIf { isActive }
    }.takeWhile { it != null }.map { it!! }
    return finiteFlow
  }

  override suspend fun <T> withBackgroundProgressInternal(
    project: Project,
    title: @ProgressTitle String,
    cancellation: TaskCancellation,
    suspender: TaskSuspender?,
    visibleInStatusBar: Boolean,
    action: suspend CoroutineScope.() -> T,
  ): T = coroutineScope {
    if (!isRhizomeProgressEnabled) {
      return@coroutineScope withBackgroundProgressInternalOld(project, title, cancellation, suspender,
                                                              visibleInStatusBar = visibleInStatusBar, action)
    }

    LOG.trace { "Task received: title=$title, project=$project" }

    val taskSuspender = retrieveSuspender(suspender)
    val pipe = cs.createProgressPipe()

    val taskContext = currentCoroutineContext()
    val taskInfoEntityJob = cs.createTaskInfoEntity(project, title, cancellation, taskSuspender, visibleInStatusBar, taskContext, pipe)

    try {
      taskSuspender?.attachTask()
      withContext(taskSuspender?.asContextElement() ?: EmptyCoroutineContext) {
        pipe.collectProgressUpdates(action)
      }
    }
    finally {
      LOG.trace { "Task finished: title=$title" }
      taskSuspender?.detachTask()
      taskInfoEntityJob.cancel()
    }
  }

  private fun TaskSuspender?.getSuspendableInfo(): TaskSuspension {
    return when (this) {
      is TaskSuspenderImpl -> TaskSuspension.Suspendable(defaultSuspendedReason)
      is BridgeTaskSuspender -> isSuspendable.value
      else -> TaskSuspension.NonSuspendable
    }
  }

  private fun CoroutineScope.createTaskInfoEntity(
    project: Project,
    title: String,
    cancellation: TaskCancellation,
    suspender: TaskSuspender?,
    visibleInStatusBar: Boolean,
    taskContext: CoroutineContext,
    pipe: ProgressPipe,
  ): Job = launch {
    val taskStorage = TaskStorage.getInstance()

    val taskInfoEntity = taskStorage.addTask(project, title, cancellation, suspender.getSuspendableInfo(), visibleInStatusBar) ?: return@launch
    val entityId = taskInfoEntity.eid
    LOG.trace { "Task added to storage: entityId=$entityId, title=$title" }

    try {
      subscribeToTask(taskInfoEntity, taskContext, suspender, pipe)
    }
    finally {
      withContext(NonCancellable) {
        taskStorage.removeTask(taskInfoEntity)
        LOG.trace { "Task removed from storage: entityId=$entityId, title=$title" }
      }
    }
  }

  private suspend fun subscribeToTask(
    taskInfo: TaskInfoEntity,
    taskContext: CoroutineContext,
    taskSuspender: TaskSuspender?,
    pipe: ProgressPipe,
  ) {
    coroutineScope {
      withKernel {
        tryWithEntities(taskInfo) {
          subscribeToTaskCancellation(taskInfo, taskContext)
          subscribeToTaskSuspensionChanges(taskInfo, taskSuspender)
          subscribeToTaskUpdates(taskInfo, pipe)
        }
      }
    }
  }

  private fun CoroutineScope.subscribeToTaskCancellation(
    taskInfo: TaskInfoEntity,
    context: CoroutineContext,
  ) {
    val title = taskInfo.title
    val entityId = taskInfo.eid

    launch {
      taskInfo.statuses
        .filter { it is TaskStatus.Canceled }
        .collect {
          LOG.trace { "Task was cancelled, entityId=$entityId, title=$title" }
          context.cancel()
        }
    }
  }

  private fun CoroutineScope.subscribeToTaskSuspensionChanges(
    taskInfo: TaskInfoEntity,
    taskSuspender: TaskSuspender?,
  ) {
    if (taskSuspender == null) return

    // Task suspension is not going to change, we can subscribe directly to statuses
    @Suppress("DEPRECATION")
    if (taskSuspender !is BridgeTaskSuspender) {
      subscribeToTaskStatus(taskInfo, taskSuspender)
      return
    }

    val title = taskInfo.title
    val entityId = taskInfo.eid
    val taskStorage = TaskStorage.getInstance()
    launch {
      taskSuspender.isSuspendable.collectLatest { suspension ->
        LOG.trace { "Task suspension changed to $suspension, entityId=$entityId, title=$title" }

        taskStorage.updateTask(taskInfo) {
          taskInfo[TaskInfoEntity.TaskSuspensionType] = suspension
        }

        if (suspension is TaskSuspension.Suspendable) {
          // Ensure that subscribeToTaskStatus is canceled when we receive a new isSuspendable value
          coroutineScope {
            subscribeToTaskStatus(taskInfo, taskSuspender)
          }
        } else {
          // Set status to Active in case the task was paused when isSuspendable changed
          TaskManager.resumeTask(taskInfo, TaskStatus.Source.SYSTEM)
        }
      }
    }
  }

  private fun CoroutineScope.subscribeToTaskStatus(
    taskInfo: TaskInfoEntity,
    taskSuspender: TaskSuspender?,
  ) {
    val title = taskInfo.title
    val entityId = taskInfo.eid

    launch {
      taskSuspender?.state?.collectLatest { state ->
        LOG.trace { "Task suspender state changed to $state, entityId=$entityId, title=$title" }
        when (state) {
          TaskSuspenderState.Active -> TaskManager.resumeTask(taskInfo, TaskStatus.Source.SYSTEM)
          is TaskSuspenderState.Paused -> TaskManager.pauseTask(taskInfo, state.suspendedReason, TaskStatus.Source.SYSTEM)
        }
      }
    }

    launch {
      // We shouldn't process events generated by TaskSuspender to avoid infinite update cycles
      taskInfo.statuses
        .filter { it.source != TaskStatus.Source.SYSTEM }
        .collect { status ->
          LOG.trace { "Task status changed to $status, entityId=$entityId, title=$title" }
          when (status) {
            is TaskStatus.Running -> taskSuspender?.resume()
            is TaskStatus.Paused -> taskSuspender?.pause(status.reason)
            is TaskStatus.Canceled -> { /* do nothing, processed by subscribeToTaskCancellation */ }
          }
        }
    }
  }

  private fun CoroutineScope.subscribeToTaskUpdates(taskInfo: TaskInfoEntity, pipe: ProgressPipe) {
    val taskStorage = TaskStorage.getInstance()
    launch {
      pipe.progressUpdates().collect { state ->
        taskStorage.updateTask(taskInfo) {
          taskInfo[TaskInfoEntity.ProgressStateType] = state
        }
      }
    }
  }

  private suspend fun <T> withBackgroundProgressInternalOld(
    project: Project,
    title: @ProgressTitle String,
    cancellation: TaskCancellation,
    providedSuspender: TaskSuspender?,
    visibleInStatusBar: Boolean,
    action: suspend CoroutineScope.() -> T,
  ): T = coroutineScope {
    val taskJob = coroutineContext.job
    val pipe = cs.createProgressPipe()
    val progressModel = ProgressIndicatorModel(title, cancellation,
                                               visibleInStatusBar = visibleInStatusBar,
                                               onCancel =  {taskJob.cancel()})

    val taskSuspender = retrieveSuspender(providedSuspender)
    taskSuspender?.attachTask()

    // has to be called before showIndicator to avoid the indicator being stopped by ProgressManager.runProcess
    val suspenderSynchronizer = progressModel.getProgressIndicator().markSuspendableIfNeeded(taskSuspender)

    val showIndicatorJob = cs.showIndicator(project, progressModel, pipe.progressUpdates())

    try {
      progressStarted(title, cancellation, pipe.progressUpdates())
      withContext(taskSuspender?.asContextElement() ?: EmptyCoroutineContext) {
        pipe.collectProgressUpdates(action)
      }
    }
    finally {
      showIndicatorJob.cancel()
      suspenderSynchronizer?.stop()
      taskSuspender?.detachTask()
    }
  }

  private fun CoroutineScope.retrieveSuspender(providedSuspender: TaskSuspender?): TaskSuspender? {
    return providedSuspender
           ?: coroutineContext[TaskSuspenderElementKey]?.taskSuspender
           ?: coroutineContext[CoroutineSuspenderElementKey]?.coroutineSuspender?.let {
             TaskSuspenderImpl(IdeBundle.message("progress.text.paused"), it as CoroutineSuspenderImpl)
           }
  }

  private fun TaskSuspender.attachTask() {
    (this as? TaskSuspenderImpl)?.attachTask(cs)
  }

  private fun TaskSuspender.detachTask() {
    (this as? TaskSuspenderImpl)?.detachTask()
  }

  private fun ProgressIndicatorEx.markSuspendableIfNeeded(taskSuspender: TaskSuspender?): TaskToProgressSuspenderSynchronizer? {
    if (taskSuspender !is TaskSuspenderImpl) return null

    @Suppress("UsagesOfObsoleteApi")
    val progressSuspender = ProgressManager.getInstance().runProcess<ProgressSuspender>(
      { ProgressSuspender.markSuspendable(this, taskSuspender.defaultSuspendedReason) }, this)
    return TaskToProgressSuspenderSynchronizer(cs, taskSuspender, progressSuspender)
  }

  override suspend fun <T> withModalProgressInternal(
    owner: ModalTaskOwner,
    title: @ModalProgressTitle String,
    cancellation: TaskCancellation,
    action: suspend CoroutineScope.() -> T,
  ): T {
    check(isModalAwareContext()) {
      "Trying to enter modality from modal-unaware modality state (ModalityState.any). " +
      "This may lead to deadlocks, and indicates a problem with scoping."
    }
    @OptIn(ExperimentalStdlibApi::class)
    val dispatcher = currentCoroutineContext()[CoroutineDispatcher.Key]
    return withContext(Dispatchers.EDT) {
      val descriptor = ModalIndicatorDescriptor(owner, title, cancellation)
      runWithModalProgressBlockingInternal(dispatcher, descriptor, action)
    }
  }

  override fun <T> runWithModalProgressBlockingInternal(
    owner: ModalTaskOwner,
    title: @ModalProgressTitle String,
    cancellation: TaskCancellation,
    action: suspend CoroutineScope.() -> T,
  ): T {
    if (application.holdsReadLock()) {
      error("This thread holds a read lock while trying to invoke a modal progress." +
            "Modal progresses are allowed only under write-intent lock because they need to prevent background write actions." +
            "Consider moving this modal progress out of `readAction`")
    }
    if (application.isWriteAccessAllowed) {
      logger<PlatformTaskSupport>().error("This thread holds write lock while trying to invoke a modal progress." +
                                          "Write actions should be fast so they do not stall the progress in the IDE." +
                                          "Consider moving your modal computation outside write action and apply the result of the computation in a different EDT event.")
    }
    return prepareThreadContext { ctx ->
      val descriptor = ModalIndicatorDescriptor(owner, title, cancellation)
      val scope = CoroutineScope(ctx + ClientId.coroutineContext())
      try {
        scope.runWithModalProgressBlockingInternal(dispatcher = null, descriptor, action)
      }
      catch (pce: ProcessCanceledException) {
        throw pce
      }
      catch (ce: CancellationException) {
        throw CeProcessCanceledException(ce)
      }
    }
  }

  @OptIn(IntellijInternalApi::class)
  private fun <T> CoroutineScope.runWithModalProgressBlockingInternal(
    dispatcher: CoroutineDispatcher?,
    descriptor: ModalIndicatorDescriptor,
    action: suspend CoroutineScope.() -> T,
  ): T {
    return inModalContext(JobProviderWithOwnerContext(coroutineContext.job, descriptor.owner)) { newModalityState ->
      val deferredDialog = CompletableDeferred<DialogWrapper>()
      val dispatcherCtx = dispatcher ?: EmptyCoroutineContext
      val modalityContext = newModalityState.asContextElement()
      val pipe = cs.createProgressPipe()
      val (permitCtx, cleanup) = getLockPermitContext(true)
      val taskJob = async(dispatcherCtx + modalityContext + permitCtx) {
        progressStarted(descriptor.title, descriptor.cancellation, pipe.progressUpdates())
        // an unhandled exception in `async` can kill the entire computation tree
        // we need to propagate the exception to the caller, since they may have some way to handle it.
        runCatching {
          handleCurrentThreadScopeCoroutines { pipe.collectProgressUpdates(action) }
        }
      }
      val modalJob = cs.launch(modalityContext) {
        val showIndicatorJob = showModalIndicator(taskJob, descriptor, pipe.progressUpdates(), deferredDialog)
        try {
          taskJob.join()
        }
        finally {
          showIndicatorJob.cancel()
        }
      }
      modalJob.invokeOnCompletion {
        // Unblock `getNextEvent()` in case it's blocked.
        SwingUtilities.invokeLater(EmptyRunnable.INSTANCE)
      }
      resetThreadLocalEventLoop {
        IdeEventQueue.getInstance().pumpEventsForHierarchy(
          exitCondition = modalJob::isCompleted,
          modalComponent = deferredDialog::modalComponent,
        )
      }
      try {
        @OptIn(ExperimentalCoroutinesApi::class)
        taskJob.getCompleted().getOrThrow()
      }
      finally {
        cleanup.finish()
      }
    }
  }
}

/**
 * We are installing a nested _modal_ event loop, so the EDT coroutines launched in immediate dispatcher must go to the modal loop,
 * and not to the unconfined loop as they do now.
 */
@Suppress("INVISIBLE_REFERENCE")
private inline fun <T> resetThreadLocalEventLoop(action: () -> T): T {
  val existingEventLoop = ThreadLocalEventLoop.currentOrNull()
  ThreadLocalEventLoop.resetEventLoop()
  try {
    return action()
  }
  finally {
    if (existingEventLoop != null) {
      ThreadLocalEventLoop.setEventLoop(existingEventLoop)
    }
  }
}

private class JobProviderWithOwnerContext(val modalJob: Job, val owner: ModalTaskOwner) : JobProvider {
  override fun isPartOf(frame: JFrame, project: Project?): Boolean {
    return when (owner) {
      is ComponentModalTaskOwner -> ProgressWindow.calcParentWindow(owner.component, null) === frame
      is ProjectModalTaskOwner -> owner.project === project
      is GuessModalTaskOwner -> ProgressWindow.calcParentWindow(null, null) === frame
    }
  }

  override fun getJob(): Job = modalJob
}

@OptIn(InternalCoroutinesApi::class)
private suspend fun <T> handleCurrentThreadScopeCoroutines(action: suspend () -> T): T {
  val (result, coroutinesResult) = withCurrentThreadCoroutineScope {
    action()
  }
  coroutinesResult.apply {
    join()
    getCancellationException().cause?.let { throw it }
  }
  return result
}

private val progressManagerTracer by lazy {
  TelemetryManager.getInstance().getSimpleTracer(ProgressManagerScope)
}

internal fun CoroutineScope.showIndicator(
  project: Project,
  progressModel: ProgressModel,
  stateFlow: Flow<ProgressState>,
): Job {
  return launch(Dispatchers.Default) {
    delay(DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS.toLong())
    withContext(progressManagerTracer.span("Progress: ${progressModel.title}")) {
      withContext(Dispatchers.EDT) {
        val taskInfo = taskInfo(progressModel.title, progressModel.cancellation)
        try {
          LOG.trace { "Showing indicator for task: ${progressModel.title}" }
          val indicatorAdded = showIndicatorInUI(project, taskInfo, progressModel)
          if (progressModel is ProgressIndicatorModel) {
            progressModel.getProgressIndicator().start()
            try {
              if (indicatorAdded) {
                withContext(Dispatchers.Default) {
                  progressModel.updateFromFlow(stateFlow)
                }
              }
            }
            finally {
              progressModel.getProgressIndicator().stop()
            }
          }
        }
        finally {
          if (progressModel is ProgressIndicatorModel) {
            LOG.trace { "Hiding indicator for task: ${progressModel.title}" }
            progressModel.finish(taskInfo) // removes indicator from UI if added
          }
        }
      }
    }
  }
}

/**
 * Asynchronously updates the indicator [text][ProgressIndicator.setText],
 * [text2][ProgressIndicator.setText2], and [fraction][ProgressIndicator.setFraction] from the [updates].
 */
@Internal
suspend fun ProgressIndicatorModel.updateFromFlow(updates: Flow<ProgressState>): Nothing {
  updates.throttle(50).flowOn(Dispatchers.Default).collect { state: ProgressState ->
    setText(state.text)
    setText2(state.details)
    state.fraction?.let {
      // first fraction update makes the indicator determinate
      setIndeterminate(false)
      setFraction(it)
    }
  }
  error("collect call must be cancelled")
}

/**
 * Asynchronously updates the indicator [text][ProgressIndicator.setText],
 * [text2][ProgressIndicator.setText2], and [fraction][ProgressIndicator.setFraction] from the [updates].
 */
@Internal
suspend fun ProgressIndicatorEx.updateFromFlow(updates: Flow<ProgressState>): Nothing {
  updates.throttle(50).flowOn(Dispatchers.Default).collect { state: ProgressState ->
    text = state.text
    text2 = state.details
    state.fraction?.let {
      // first fraction update makes the indicator determinate
      isIndeterminate = false
      fraction = it
    }
  }
  error("collect call must be cancelled")
}

private fun showIndicatorInUI(project: Project, taskInfo: TaskInfo, progressModel: ProgressModel): Boolean {
  val frameEx: IdeFrameEx = WindowManagerEx.getInstanceEx().findFrameHelper(project) ?: return false
  val statusBar = frameEx.statusBar as? IdeStatusBarImpl ?: return false
  statusBar.addProgressImpl(progressModel, taskInfo)
  return true
}

internal fun taskInfo(title: @ProgressTitle String, cancellation: TaskCancellation): TaskInfo = object : TaskInfo {
  override fun getTitle(): String = title
  override fun isCancellable(): Boolean = cancellation is CancellableTaskCancellation
  override fun getCancelText(): String? = (cancellation as? CancellableTaskCancellation)?.buttonText
  override fun getCancelTooltipText(): String? = (cancellation as? CancellableTaskCancellation)?.tooltipText
}

private class ModalIndicatorDescriptor(
  val owner: ModalTaskOwner,
  val title: @ModalProgressTitle String,
  val cancellation: TaskCancellation,
)

private fun CoroutineScope.showModalIndicator(
  taskJob: Job,
  descriptor: ModalIndicatorDescriptor,
  stateFlow: Flow<ProgressState>,
  deferredDialog: CompletableDeferred<DialogWrapper>?,
): Job = launch(Dispatchers.Default) {
  try {
    supervisorScope {
      if (isHeadlessEnv()) {
        return@supervisorScope
      }
      delay(DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS.toLong())
      doShowModalIndicator(taskJob, descriptor, stateFlow, deferredDialog)
    }
  }
  catch (ce: CancellationException) {
    throw ce
  }
  catch (t: Throwable) {
    logger<PlatformTaskSupport>().error(t)
  }
}

private suspend fun doShowModalIndicator(
  mainJob: Job,
  descriptor: ModalIndicatorDescriptor,
  stateFlow: Flow<ProgressState>,
  deferredDialog: CompletableDeferred<DialogWrapper>?,
) {
  // Use Dispatchers.Main to avoid showing the dialog on top of another unrelated modal dialog (e.g. MessageDialogBuilder.YesNoCancel)
  // we need to avoid running `dialog.show()` in WI because it would prevent background WA
  // hence, we are using `Dispatchers.Main` to permit locks inside, but not by default
  require(coroutineContext.contextModality() != null)
  withContext(Dispatchers.Main) {
    val window = ownerWindow(descriptor.owner)
    if (window == null) {
      logger<PlatformTaskSupport>().error("Cannot show progress dialog because owner window is not found")
      return@withContext
    }

    val ui = ProgressDialogUI()
    ui.initCancellation(descriptor.cancellation) {
      mainJob.cancel("button cancel")
    }
    ui.backgroundButton.isVisible = false
    ui.updateTitle(descriptor.title)
    launch {
      ui.updateFromFlow(stateFlow)
    }
    val dialog = createDialogWrapper(
      panel = ui.panel,
      window = window,
      writeAction = false,
      project = (descriptor.owner as? ProjectModalTaskOwner)?.project,
      cancelAction = {
        if (descriptor.cancellation is CancellableTaskCancellation) {
          mainJob.cancel("dialog cancel")
        }
      },
    )

    @OptIn(AwaitCancellationAndInvoke::class)
    awaitCancellationAndInvoke {
      dialog.close(DialogWrapper.OK_EXIT_CODE)
    }

    if (ApplicationManagerEx.isInIntegrationTest()) {
      logger<PlatformTaskSupport>().info("Modal dialog is shown: ${descriptor.title}")
    }

    // 1. If the dialog is heavy (= spins an inner event loop):
    // show() returns after dialog was closed
    // => following withContext() will resume with CancellationException
    // => don't complete deferredDialog
    // 2. If the dialog is glass pane based (= without inner event loop):
    // show() returns immediately
    // => complete deferredDialog to process component inputs events in processEventQueueConsumingUnrelatedInputEvents
    dialog.show()

    // 'Light' popup is shown in glass pane,
    // glass pane is 'activating' (becomes visible) in 'SwingUtilities.invokeLater' call (see IdeGlassPaneImp.addImpl),
    // requesting focus to cancel button until that time has no effect, as it's not showing
    // => re-dispatch via 'SwingUtilities.invokeLater'
    withContext(RawSwingDispatcher) {
      val focusComponent = ui.cancelButton
      val previousFocusOwner = SwingUtilities.getWindowAncestor(focusComponent)?.mostRecentFocusOwner
      focusComponent.requestFocusInWindow()
      if (previousFocusOwner != null) {
        @OptIn(AwaitCancellationAndInvoke::class)
        awaitCancellationAndInvoke {
          // TODO: don't move focus back if the focus owner was changed
          //if (focusComponent.isFocusOwner)
          previousFocusOwner.requestFocusInWindow()
        }
      }

      deferredDialog?.complete(dialog)
    }
  }
}

private fun ownerWindow(owner: ModalTaskOwner): Window? {
  return when (owner) {
    is ComponentModalTaskOwner -> ProgressWindow.calcParentWindow(owner.component, null)
    is ProjectModalTaskOwner -> ProgressWindow.calcParentWindow(null, owner.project)
    is GuessModalTaskOwner -> ProgressWindow.calcParentWindow(null, null) // guess
  }
}

private suspend fun ProgressDialogUI.updateFromFlow(updates: Flow<ProgressState>): Nothing {
  updates
    .throttle(50)
    .flowOn(Dispatchers.Default)
    .collect {
      updateProgress(it)
    }
  error("collect call must be cancelled")
}

private fun Deferred<DialogWrapper>.modalComponent(): Container? {
  if (!isCompleted) {
    return null
  }
  @OptIn(ExperimentalCoroutinesApi::class)
  val dialogWrapper = getCompleted()
  if (dialogWrapper.isDisposed) {
    return null
  }
  return dialogWrapper.contentPane
}

private fun IdeEventQueue.pumpEventsForHierarchy(
  exitCondition: () -> Boolean,
  modalComponent: () -> Component?,
) {
  assert(EventQueue.isDispatchThread())
  while (!exitCondition()) {
    val event: AWTEvent = nextEvent
    val consumed = consumeUnrelatedEvent(modalComponent(), event)
    if (!consumed) {
      dispatchEvent(event)
    }
  }
}

@Internal
fun IdeEventQueue.pumpEventsForHierarchy(exitCondition: () -> Boolean) {
  resetThreadContext {
    pumpEventsForHierarchy(
      exitCondition = exitCondition,
      modalComponent = { null },
    )
  }
}
