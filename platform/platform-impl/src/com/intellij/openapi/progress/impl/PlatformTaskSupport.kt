// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.impl

import com.intellij.concurrency.currentThreadContext
import com.intellij.concurrency.resetThreadContext
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.impl.RawSwingDispatcher
import com.intellij.openapi.application.impl.inModalContext
import com.intellij.openapi.application.impl.onEdtInNonAnyModality
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.*
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase
import com.intellij.openapi.progress.util.ProgressDialogUI
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.progress.util.ProgressIndicatorWithDelayedPresentation.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS
import com.intellij.openapi.progress.util.createDialogWrapper
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import java.awt.Component
import java.awt.event.InputEvent
import javax.swing.SwingUtilities

internal class PlatformTaskSupport : TaskSupport {

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
    inModalContext(cs.coroutineContext.job) { newModalityState ->
      val deferredDialog = CompletableDeferred<DialogWrapper>()
      val mainJob = cs.async(Dispatchers.Default + newModalityState.asContextElement()) {
        withModalIndicator(descriptor, deferredDialog, action)
      }
      runBlocking {
        // Dispatch EDT events in the current runBlocking context.
        val processEventQueueJob = processEventQueueConsumingUnrelatedInputEvents(deferredDialog)
        mainJob.invokeOnCompletion {
          // Stop processing the events when the task (with its subtasks) is completed.
          processEventQueueJob.cancel()
          // Unblock `getNextEvent()` in case it's blocked.
          // It's important that getNextEvent() returns after [processEventQueueJob] is cancelled,
          // this way the next yield() call throws CancellationException.
          SwingUtilities.invokeLater(EmptyRunnable.INSTANCE)
        }
        mainJob.await()
      }
    }
  }
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

private suspend fun <T> withModalIndicator(
  descriptor: ModalIndicatorDescriptor,
  deferredDialog: CompletableDeferred<DialogWrapper>?,
  action: suspend CoroutineScope.() -> T,
): T = coroutineScope {
  val sink = FlowProgressSink()
  val showIndicatorJob = showModalIndicator(descriptor, sink.stateFlow, deferredDialog)
  try {
    withContext(sink.asContextElement(), action)
  }
  finally {
    showIndicatorJob.cancel()
  }
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

/**
 * Before [deferredDialog] is completed, all input events are consumed unconditionally,
 * because the absence of the visible dialog means that
 * [com.intellij.ide.IdeEventQueue.consumeUnrelatedEvent] would consume the event.
 *
 * Once [deferredDialog] is completed (glass pane dialog is visible), input events originating in the dialog will be dispatched.
 * [deferredDialog] might never be completed:
 * - in case the dialog is heavy, all input events will be handled by the inner event loop;
 * - in case the dialog never became visible because the task was completed in [DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS] ms,
 * the processing routine will be simply cancelled.
 */
private fun CoroutineScope.processEventQueueConsumingUnrelatedInputEvents(deferredDialog: Deferred<DialogWrapper>): Job = launch {
  val eventQueue = IdeEventQueue.getInstance()
  val processConsumingAllInputEventsUnconditionallyJob = launch {
    while (true) {
      val event = eventQueue.nextEvent
      if (event is InputEvent && event.source is Component) {
        event.consume()
      }
      else {
        eventQueue.dispatchEvent(event)
      }
      yield()
    }
  }
  val modalComponent = deferredDialog.await().contentPane
  processConsumingAllInputEventsUnconditionallyJob.cancel()
  while (true) {
    val event = eventQueue.nextEvent
    if (!IdeEventQueue.consumeUnrelatedEvent(modalComponent, event)) {
      eventQueue.dispatchEvent(event)
    }
    yield()
  }
}
