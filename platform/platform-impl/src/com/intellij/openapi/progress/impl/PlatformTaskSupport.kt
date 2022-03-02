// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.impl

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.impl.withModalContext
import com.intellij.openapi.progress.*
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase
import com.intellij.openapi.progress.util.ProgressDialogUI
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.progress.util.ProgressIndicatorWithDelayedPresentation.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS
import com.intellij.openapi.progress.util.createDialogWrapper
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.NlsContexts.ProgressTitle
import com.intellij.openapi.wm.ex.IdeFrameEx
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import com.intellij.openapi.wm.ex.StatusBarEx
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.util.flow.throttle
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOn
import java.awt.Component
import javax.swing.SwingUtilities

internal class PlatformTaskSupport : TaskSupport {

  override fun taskCancellationNonCancellableInternal(): TaskCancellation = NonCancellableTaskCancellation

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
  ): T = withModalContext {
    val sink = FlowProgressSink()
    val showIndicatorJob = showModalIndicator(owner, title, cancellation, sink.stateFlow)
    try {
      withContext(sink.asContextElement(), action)
    }
    finally {
      showIndicatorJob.cancel()
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
    }
    fraction = state.fraction
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

private fun CoroutineScope.showModalIndicator(
  owner: ModalTaskOwner,
  title: @ProgressTitle String,
  cancellation: TaskCancellation,
  stateFlow: Flow<ProgressState>,
): Job = launch(Dispatchers.IO) {
  delay(DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS.toLong())
  val mainJob = this@showModalIndicator.coroutineContext.job
  withContext(Dispatchers.EDT) {
    val window = ownerWindow(owner) ?: return@withContext
    val ui = ProgressDialogUI()
    ui.initCancellation(cancellation) {
      mainJob.cancel("button cancel")
    }
    ui.backgroundButton.isVisible = false
    ui.updateTitle(title)
    launch {
      ui.updateFromSink(stateFlow)
    }
    val dialog = createDialogWrapper(
      panel = ui.panel,
      window = window,
      writeAction = false,
      project = (owner as? ProjectModalTaskOwner)?.project,
      cancelAction = {
        if (cancellation is CancellableTaskCancellation) {
          mainJob.cancel("dialog cancel")
        }
      },
    )
    launch { // will be run in an inner event loop
      val focusComponent = ui.cancelButton
      val previousFocusOwner = SwingUtilities.getWindowAncestor(focusComponent)?.mostRecentFocusOwner
      focusComponent.requestFocusInWindow()
      try {
        awaitCancellation()
      }
      finally {
        dialog.close(DialogWrapper.OK_EXIT_CODE)
        previousFocusOwner?.requestFocusInWindow()
      }
    }
    dialog.show() // will spin an inner event loop
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
