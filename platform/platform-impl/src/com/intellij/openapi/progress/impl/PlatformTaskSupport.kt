// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.impl

import com.intellij.concurrency.currentThreadContext
import com.intellij.concurrency.resetThreadContext
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.impl.JobProvider
import com.intellij.openapi.application.impl.RawSwingDispatcher
import com.intellij.openapi.application.impl.inModalContext
import com.intellij.openapi.application.impl.onEdtInNonAnyModality
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.*
import com.intellij.openapi.progress.util.*
import com.intellij.openapi.progress.util.ProgressIndicatorWithDelayedPresentation.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.impl.DialogWrapperPeerImpl.isHeadlessEnv
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.util.NlsContexts.ProgressTitle
import com.intellij.openapi.wm.ex.IdeFrameEx
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import com.intellij.openapi.wm.ex.StatusBarEx
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.util.awaitCancellation
import com.intellij.util.flow.throttle
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.AWTEvent
import java.awt.Component
import java.awt.Container
import java.awt.EventQueue
import javax.swing.JFrame
import javax.swing.SwingUtilities
import kotlin.coroutines.coroutineContext

@Internal
class PlatformTaskSupport : TaskSupport {

  data class ProgressStartedEvent(
    val title: @ProgressTitle String,
    val updates: Flow<ProgressState>, // finite
  )

  private val _progressStarted: MutableSharedFlow<ProgressStartedEvent> = MutableSharedFlow()

  val progressStarted: SharedFlow<ProgressStartedEvent> = _progressStarted.asSharedFlow()

  private suspend fun progressStarted(title: @ProgressTitle String, updates: Flow<ProgressState>) {
    val job = coroutineContext.job
    _progressStarted.emit(ProgressStartedEvent(title, updates.takeWhile { job.isActive }))
  }

  override fun taskCancellationNonCancellableInternal(): TaskCancellation.NonCancellable = NonCancellableTaskCancellation

  override fun taskCancellationCancellableInternal(): TaskCancellation.Cancellable = defaultCancellable

  override fun modalTaskOwner(component: Component): ModalTaskOwner = ComponentModalTaskOwner(component)

  override fun modalTaskOwner(project: Project): ModalTaskOwner = ProjectModalTaskOwner(project)

  override suspend fun <T> withBackgroundProgressIndicatorInternal(
    project: Project,
    title: @ProgressTitle String,
    cancellation: TaskCancellation,
    action: suspend CoroutineScope.() -> T
  ): T = coroutineScope {
    val sink = FlowProgressSink()
    progressStarted(title, sink.stateFlow)
    val showIndicatorJob = showIndicator(project, taskInfo(title, cancellation), sink.stateFlow)
    try {
      withContext(sink.asContextElement(), action)
    }
    finally {
      showIndicatorJob.cancel()
    }
  }

  override suspend fun <T> withModalProgressIndicatorInternal(
    owner: ModalTaskOwner,
    title: @ProgressTitle String,
    cancellation: TaskCancellation,
    action: suspend CoroutineScope.() -> T,
  ): T = onEdtInNonAnyModality {
    val descriptor = ModalIndicatorDescriptor(owner, title, cancellation)
    runBlockingModalInternal(cs = this, descriptor, action)
  }

  override fun <T> runBlockingModalInternal(
    owner: ModalTaskOwner,
    title: @ProgressTitle String,
    cancellation: TaskCancellation,
    action: suspend CoroutineScope.() -> T,
  ): T = ensureCurrentJobAllowingOrphan {
    val descriptor = ModalIndicatorDescriptor(owner, title, cancellation)
    val scope = CoroutineScope(currentThreadContext())
    runBlockingModalInternal(cs = scope, descriptor, action)
  }

  private fun <T> runBlockingModalInternal(
    cs: CoroutineScope,
    descriptor: ModalIndicatorDescriptor,
    action: suspend CoroutineScope.() -> T,
  ): T = resetThreadContext().use {
    inModalContext(JobProviderWithOwnerContext(cs.coroutineContext.job, descriptor.owner)) { newModalityState ->
      val deferredDialog = CompletableDeferred<DialogWrapper>()
      val mainJob = cs.async(Dispatchers.Default + newModalityState.asContextElement()) {
        val sink = FlowProgressSink()
        progressStarted(descriptor.title, sink.stateFlow)
        val showIndicatorJob = showModalIndicator(descriptor, sink.stateFlow, deferredDialog)
        try {
          withContext(sink.asContextElement(), action)
        }
        finally {
          showIndicatorJob.cancel()
        }
      }
      mainJob.invokeOnCompletion {
        // Unblock `getNextEvent()` in case it's blocked.
        SwingUtilities.invokeLater(EmptyRunnable.INSTANCE)
      }
      IdeEventQueue.getInstance().pumpEventsForHierarchy(
        exitCondition = mainJob::isCompleted,
        modalComponent = deferredDialog::modalComponent,
      )
      @OptIn(ExperimentalCoroutinesApi::class)
      mainJob.getCompleted()
    }
  }
}

private class JobProviderWithOwnerContext(val modalJob: Job, val owner: ModalTaskOwner) : JobProvider {
  override fun isPartOf(frame: JFrame, project: Project?): Boolean {
    return when (owner) {
      is ComponentModalTaskOwner -> ProgressWindow.calcParentWindow(owner.component, null) === frame
      is ProjectModalTaskOwner -> owner.project === project
      else -> ProgressWindow.calcParentWindow(null, null) === frame
    }
  }

  override fun getJob(): Job = modalJob
}

private fun CoroutineScope.showIndicator(
  project: Project,
  taskInfo: TaskInfo,
  stateFlow: Flow<ProgressState>,
): Job {
  return launch(Dispatchers.IO) {
    delay(DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS.toLong())
    val indicator = coroutineCancellingIndicator(this@showIndicator.coroutineContext.job) // cancel the parent job from UI
    indicator.start()
    try {
      val indicatorAdded = withContext(Dispatchers.EDT) {
        showIndicatorInUI(project, taskInfo, indicator)
      }
      if (indicatorAdded) {
        indicator.updateFromSink(stateFlow)
      }
    }
    finally {
      indicator.stop()
      indicator.finish(taskInfo)
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
 * [text2][ProgressIndicator.setText2], and [fraction][ProgressIndicator.setFraction] from the [stateFlow].
 */
private suspend fun ProgressIndicatorEx.updateFromSink(stateFlow: Flow<ProgressState>): Nothing {
  stateFlow.collect { state: ProgressState ->
    text = state.text
    text2 = state.details
    if (state.fraction >= 0.0) {
      // first fraction update makes the indicator determinate
      isIndeterminate = false
      fraction = state.fraction
    }
  }
  error("collect call must be cancelled")
}

private fun showIndicatorInUI(project: Project, taskInfo: TaskInfo, indicator: ProgressIndicatorEx): Boolean {
  val frameEx: IdeFrameEx = WindowManagerEx.getInstanceEx().findFrameHelper(project) ?: return false
  val statusBar = frameEx.statusBar as? StatusBarEx ?: return false
  statusBar.addProgress(indicator, taskInfo)
  return true
}

private fun taskInfo(title: @ProgressTitle String, cancellation: TaskCancellation): TaskInfo = object : TaskInfo {
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

@OptIn(IntellijInternalApi::class)
private fun CoroutineScope.showModalIndicator(
  descriptor: ModalIndicatorDescriptor,
  stateFlow: Flow<ProgressState>,
  deferredDialog: CompletableDeferred<DialogWrapper>?,
): Job = launch(Dispatchers.IO) {
  if (isHeadlessEnv()) {
    return@launch
  }
  delay(DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS.toLong())
  val mainJob = this@showModalIndicator.coroutineContext.job
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
      ui.updateFromSink(stateFlow)
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

    awaitCancellation {
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
        awaitCancellation {
          previousFocusOwner.requestFocusInWindow()
        }
      }

      deferredDialog?.complete(dialog)
    }
  }
}

private suspend fun ProgressDialogUI.updateFromSink(stateFlow: Flow<ProgressState>): Nothing {
  stateFlow
    .throttle(50)
    .flowOn(Dispatchers.IO)
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
    val consumed = IdeEventQueue.consumeUnrelatedEvent(modalComponent(), event)
    if (!consumed) {
      dispatchEvent(event)
    }
  }
}
