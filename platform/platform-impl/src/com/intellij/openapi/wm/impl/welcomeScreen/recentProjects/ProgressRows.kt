// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.recentProjects

import com.intellij.CommonBundle
import com.intellij.ide.IdeBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressIndicatorModel
import com.intellij.openapi.progress.ProgressModel
import com.intellij.openapi.progress.TaskInfo
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.panel.ProgressPanel
import com.intellij.openapi.ui.panel.ProgressPanelBuilder
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsContexts.ProgressTitle
import com.intellij.openapi.wm.impl.status.ProcessPopup
import com.intellij.openapi.wm.impl.status.ProgressComponent
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.ui.ClientProperty
import javax.swing.JPanel

/**
 * Draws one [RecentProjectProgress] with the component the status bar draws its own processes with.
 *
 * [ProgressComponent] takes a [TaskInfo] and a mutable [ProgressModel], and reports its repaints from the thread that reported the
 * progress. This owns all three, so the rest of the welcome screen hands over values and takes repaints on the thread it chooses.
 */
internal class ProgressRow(
  private val popup: ProcessPopup,
  progress: RecentProjectProgress,
  onRepaintNeeded: () -> Unit,
  onCancel: (ProgressId) -> Unit,
) : Disposable {
  private var shown = progress
  private val model = ProgressIndicatorModel(
    title = progress.title,
    taskCancellation = cancellation(progress),
    visibleInStatusBar = false,
    onCancel = { cancelShown(onCancel) },
  )
  private val indicator = TaskProgressComponent(RowTaskInfo(progress), model, onRepaintNeeded)

  init {
    popup.addIndicator(indicator)
  }

  fun repaint() {
    indicator.updateAndRepaint()
  }

  fun show(progress: RecentProjectProgress) {
    shown = progress
    model.setText(progress.text)
    // A fraction on an indeterminate indicator logs and turns the indicator determinate, so an operation without one gets no fraction.
    val fraction = progress.fraction
    if (fraction == null) {
      model.setIndeterminate(true)
    }
    else {
      model.setIndeterminate(false)
      model.setFraction(fraction)
    }
    repaint()
  }

  override fun dispose() {
    popup.removeIndicator(indicator)
    Disposer.dispose(indicator)
  }

  private fun cancelShown(onCancel: (ProgressId) -> Unit) {
    val progress = shown
    if (askToCancel(progress)) {
      onCancel(progress.id)
    }
  }
}

/**
 * The rows of a [ProcessPopup], kept in step with the operations they show.
 *
 * The popup holds its own indicators but hands none of them back, so this keeps the one row it made for each operation. A row adds
 * itself to the popup and takes itself out again, so disposing it is all this does. A row outlives every report of its operation, and
 * only an operation that starts or ends changes the window.
 */
internal class ProgressRows(
  private val popup: ProcessPopup,
  private val onRepaintNeeded: () -> Unit,
  private val onCancel: (ProgressId) -> Unit,
) : Disposable {
  private val rows = LinkedHashMap<ProgressId, ProgressRow>()

  /** Brings the window to [current], one row per operation, in the order they are reported. */
  fun show(current: List<RecentProjectProgress>) {
    (rows.keys - current.mapTo(HashSet()) { it.id }).forEach { ended ->
      rows.remove(ended)?.let(Disposer::dispose)
    }
    current.forEach { progress ->
      val row = rows.getOrPut(progress.id) {
        ProgressRow(popup, progress, onRepaintNeeded = onRepaintNeeded, onCancel = onCancel)
      }
      row.show(progress)
    }
  }

  fun repaint() {
    rows.values.forEach(ProgressRow::repaint)
  }

  override fun dispose() {
    rows.values.forEach(Disposer::dispose)
    rows.clear()
  }
}

private fun cancellation(progress: RecentProjectProgress): TaskCancellation =
  if (progress.isCancellable) TaskCancellation.cancellable().withTooltipText(progress.cancelTooltip) else TaskCancellation.nonCancellable()

/** Whether [progress] may be cancelled now: at once, or once the user has answered what it asks. */
private fun askToCancel(progress: RecentProjectProgress): Boolean {
  val question = progress.cancelQuestion ?: return true
  val exitCode = Messages.showYesNoDialog(
    question,
    progress.cancelQuestionTitle ?: progress.title,
    IdeBundle.message("action.stop"),
    IdeBundle.message("button.cancel"),
    Messages.getQuestionIcon(),
  )
  return exitCode == Messages.OK
}

/**
 * The row the status bar builds for one of its own processes.
 *
 * [com.intellij.openapi.wm.impl.status.InfoAndProgressPanel] gives its rows a [ProgressPanel], not the plain layout of
 * [ProgressComponent], and that panel is what the process window looks like.
 */
private class TaskProgressComponent(
  info: TaskInfo,
  model: ProgressModel,
  /**
   * Null while the base constructor runs.
   *
   * It subscribes to the model, and the indicator replays the state it holds to the new subscriber while it does, before Kotlin has
   * assigned a field of this class. [ProgressRow] paints the row once it is built, so that first report needs nowhere to go.
   */
  private val onRepaintNeeded: (() -> Unit)?,
) : ProgressComponent(isCompact = false, info, model) {
  private lateinit var progressPanel: ProgressPanel

  override var processNameValue: @ProgressTitle String?
    get() = progressPanel.labelText
    set(value) {
      progressPanel.setLabelText(value)
    }

  override var textValue: @NlsContexts.DetailedDescription String?
    get() = progressPanel.commentText
    set(value) {
      progressPanel.setCommentText(value)
    }

  override var text2Value: @NlsContexts.DetailedDescription String?
    get() = super.text2Value
    set(value) {
      progressPanel.setText2(value)
    }

  override fun createComponent(): JPanel {
    val panel = ProgressPanelBuilder(progress)
      .withTopSeparator()
      .withText2()
      .withCancel { cancelRequest() }
      .createPanel()
    progressPanel = ProgressPanel.getProgressPanel(progress)!!
    // An operation nobody can cancel keeps the button's place and paints nothing in it, so every bar in the window is the same length.
    progressPanel.cancelButton?.setPainting(info.isCancellable)
    ClientProperty.put(panel, ProcessPopup.KEY, progressPanel)
    return panel
  }

  override fun setTextEnabled(value: Boolean) {
    progressPanel.setCommentEnabled(value)
  }

  override fun setText2Enabled(value: Boolean) {
    progressPanel.setText2Enabled(value)
  }

  /** Reports that the row needs a repaint, instead of repainting on the thread that reported the progress. */
  override fun queueProgressUpdate() {
    onRepaintNeeded?.invoke()
  }

  init {
    processNameValue = info.title
  }
}

/** What [ProgressComponent] asks about the operation it draws. */
private class RowTaskInfo(private val progress: RecentProjectProgress) : TaskInfo {
  override fun getTitle(): @ProgressTitle String = progress.title

  override fun getCancelText(): @NlsContexts.Button String = CommonBundle.getCancelButtonText()

  override fun getCancelTooltipText(): @NlsContexts.Tooltip String = progress.cancelTooltip

  override fun isCancellable(): Boolean = progress.isCancellable
}
