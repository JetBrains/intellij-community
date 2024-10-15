// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.impl

import com.intellij.codeWithMe.ClientId
import com.intellij.concurrency.resetThreadContext
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.consumeUnrelatedEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.impl.JobProvider
import com.intellij.openapi.application.impl.RawSwingDispatcher
import com.intellij.openapi.application.impl.inModalContext
import com.intellij.openapi.application.isModalAwareContext
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.progress.*
import com.intellij.openapi.progress.util.*
import com.intellij.openapi.progress.util.ProgressIndicatorWithDelayedPresentation.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.impl.DialogWrapperPeerImpl.isHeadlessEnv
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.openapi.util.NlsContexts.ProgressTitle
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ex.IdeFrameEx
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.openapi.wm.impl.status.IdeStatusBarImpl
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.ide.progress.*
import com.intellij.platform.kernel.withKernel
import com.intellij.platform.util.coroutines.flow.throttle
import com.intellij.platform.util.progress.ProgressPipe
import com.intellij.platform.util.progress.ProgressState
import com.intellij.platform.util.progress.createProgressPipe
import com.intellij.util.awaitCancellationAndInvoke
import fleet.kernel.rete.collect
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.*
import java.io.Closeable
import javax.swing.JFrame
import javax.swing.SwingUtilities
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.coroutineContext

internal val isRhizomeProgressEnabled
  get() = Registry.`is`("rhizome.progress")

private val LOG = logger<PlatformTaskSupport>()

@Internal
class PlatformTaskSupport(private val cs: CoroutineScope) : TaskSupport {
  data class ProgressStartedEvent(
    val title: @ProgressTitle String,
    val cancellation: TaskCancellation,
    val context: CoroutineContext,
    val updates: Flow<ProgressState>, // finite
  )

  private val _progressStarted: MutableSharedFlow<ProgressStartedEvent> = MutableSharedFlow()

  val progressStarted: SharedFlow<ProgressStartedEvent> = _progressStarted.asSharedFlow()

  private suspend fun progressStarted(title: @ProgressTitle String, cancellation: TaskCancellation, updates: Flow<ProgressState>) {
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
    action: suspend CoroutineScope.() -> T
  ): T = coroutineScope {
    if (!isRhizomeProgressEnabled) {
      return@coroutineScope withBackgroundProgressInternalOld(project, title, cancellation, action)
    }

    LOG.trace { "Task received: title=$title, project=$project" }

    val context = currentCoroutineContext()
    val taskStorage = TaskStorage.getInstance()

    val pipe = cs.createProgressPipe()

    val taskInfoEntity = taskStorage.addTask(project, title, cancellation)
    val entityId = taskInfoEntity.eid
    LOG.trace { "Task added to storage: entityId=$entityId, title=$title" }

    try {
      cs.subscribeToTask(taskInfoEntity, context, pipe).use {
        pipe.collectProgressUpdates(action)
      }
    }
    finally {
      LOG.trace { "Task finished: entityId=$entityId, title=$title" }
      cs.launch {
        taskStorage.removeTask(taskInfoEntity)
        LOG.trace { "Task removed from storage: entityId=$entityId, title=$title" }
      }
    }
  }

  private fun CoroutineScope.subscribeToTask(taskInfo: TaskInfoEntity, taskContext: CoroutineContext, pipe: ProgressPipe): Closeable {
    val jobs = listOf(
      subscribeToTaskStatus(taskInfo, taskContext),
      subscribeToTaskUpdates(taskInfo, pipe)
    )

    return Closeable { jobs.forEach { it.cancel()} }
  }

  private fun CoroutineScope.subscribeToTaskStatus(taskInfo: TaskInfoEntity, context: CoroutineContext): Job {
    val title = taskInfo.title
    val entityId = taskInfo.eid
    return launch {
      withKernel {
        taskInfo.statuses.collect { status ->
          LOG.trace { "Task status changed to $status, entityId=$entityId, title=$title" }
          when (status) {
            TaskStatus.RUNNING -> { /* TODO RDCT-1620 */ }
            TaskStatus.PAUSED -> { /* TODO RDCT-1620 */ }
            TaskStatus.CANCELED -> context.cancel()
          }
        }
      }
    }
  }

  private fun CoroutineScope.subscribeToTaskUpdates(taskInfo: TaskInfoEntity, pipe: ProgressPipe): Job {
    val taskStorage = TaskStorage.getInstance()
    return launch {
      pipe.progressUpdates().collect {
        taskStorage.updateTask(taskInfo, it)
      }
    }
  }

  private suspend fun <T> withBackgroundProgressInternalOld(
    project: Project,
    title: @ProgressTitle String,
    cancellation: TaskCancellation,
    action: suspend CoroutineScope.() -> T
  ): T = coroutineScope {
    val taskJob = coroutineContext.job
    val pipe = cs.createProgressPipe()
    val indicator = coroutineCancellingIndicator(taskJob)
    val showIndicatorJob = cs.showIndicator(project, indicator, taskInfo(title, cancellation), pipe.progressUpdates())
    try {
      progressStarted(title, cancellation, pipe.progressUpdates())
      pipe.collectProgressUpdates(action)
    }
    finally {
      showIndicatorJob.cancel()
    }
  }

  override suspend fun <T> withModalProgressInternal(
    owner: ModalTaskOwner,
    title: @ProgressTitle String,
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
    title: @ProgressTitle String,
    cancellation: TaskCancellation,
    action: suspend CoroutineScope.() -> T,
  ): T = prepareThreadContext { ctx ->
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
      val taskJob = async(dispatcherCtx + modalityContext) {
          progressStarted(descriptor.title, descriptor.cancellation, pipe.progressUpdates())
          // an unhandled exception in `async` can kill the entire computation tree
          // we need to propagate the exception to the caller, since they may have some way to handle it.
          runCatching {
            pipe.collectProgressUpdates(action)
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
      IdeEventQueue.getInstance().pumpEventsForHierarchy(
        exitCondition = modalJob::isCompleted,
        modalComponent = deferredDialog::modalComponent,
      )
      @OptIn(ExperimentalCoroutinesApi::class)
      taskJob.getCompleted().getOrThrow()
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

private val progressManagerTracer by lazy {
  TelemetryManager.getInstance().getSimpleTracer(ProgressManagerScope)
}

internal fun CoroutineScope.showIndicator(
  project: Project,
  indicator: ProgressIndicatorEx,
  taskInfo: TaskInfo,
  stateFlow: Flow<ProgressState>,
): Job {
  return launch(Dispatchers.Default) {
    delay(DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS.toLong())
    withContext(progressManagerTracer.span("Progress: ${taskInfo.title}")) {
      withContext(Dispatchers.EDT) {
        LOG.trace { "Showing indicator for task: $taskInfo" }
        val indicatorAdded = showIndicatorInUI(project, taskInfo, indicator)
        try {
          indicator.start() // must be after showIndicatorInUI
          try {
            if (indicatorAdded) {
              withContext(Dispatchers.Default) {
                indicator.updateFromFlow(stateFlow)
              }
            }
          }
          finally {
            indicator.stop()
          }
        }
        finally {
          LOG.trace { "Hiding indicator for task: $taskInfo" }
          indicator.finish(taskInfo) // removes indicator from UI if added
        }
      }
    }
  }
}

/**
 * @return an indicator which cancels the given [job] when it's cancelled
 */
private fun coroutineCancellingIndicator(job: Job): ProgressIndicatorEx {
  val indicator = ProgressIndicatorBase()
  indicator.addStateDelegate(object : AbstractProgressIndicatorExBase() {
    override fun cancel() {
      job.cancel()
      super.cancel()
    }
  })
  return indicator
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

private fun showIndicatorInUI(project: Project, taskInfo: TaskInfo, indicator: ProgressIndicatorEx): Boolean {
  val frameEx: IdeFrameEx = WindowManagerEx.getInstanceEx().findFrameHelper(project) ?: return false
  val statusBar = frameEx.statusBar as? IdeStatusBarImpl ?: return false
  statusBar.addProgressImpl(indicator, taskInfo)
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
  val title: @ProgressTitle String,
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
  // Use Dispatchers.EDT to avoid showing the dialog on top of another unrelated modal dialog (e.g. MessageDialogBuilder.YesNoCancel)
  withContext(Dispatchers.EDT) {
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

    awaitCancellationAndInvoke {
      dialog.close(DialogWrapper.OK_EXIT_CODE)
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
  resetThreadContext().use {
    pumpEventsForHierarchy(
      exitCondition = exitCondition,
      modalComponent = { null },
    )
  }
}
