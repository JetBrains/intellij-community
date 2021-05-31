// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.icons.AllIcons
import com.intellij.ide.nls.NlsMessages.formatNarrowAndList
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.AppUIExecutor.onUiThread
import com.intellij.openapi.application.impl.coroutineDispatchingContext
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase
import com.intellij.openapi.progress.util.ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.changes.InclusionListener
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import com.intellij.openapi.wm.ex.StatusBarEx
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.EditorTextComponent
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI.Borders.emptyLeft
import com.intellij.util.ui.SwingHelper.createHtmlViewer
import com.intellij.util.ui.SwingHelper.setHtml
import com.intellij.util.ui.UIUtil.getErrorForeground
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.jetbrains.annotations.Nls
import javax.swing.event.HyperlinkEvent
import kotlin.properties.Delegates.observable

private fun JBLabel.setError(@NlsContexts.Label errorText: String) {
  text = errorText
  icon = AllIcons.General.Error
  foreground = getErrorForeground()
  isVisible = true
}

private fun JBLabel.setWarning(@NlsContexts.Label warningText: String) {
  text = warningText
  icon = AllIcons.General.Warning
  foreground = null
  isVisible = true
}

open class CommitProgressPanel : NonOpaquePanel(VerticalLayout(4)), CommitProgressUi, InclusionListener, DocumentListener, Disposable {
  private val scope = CoroutineScope(SupervisorJob() + onUiThread().coroutineDispatchingContext())
    .also { Disposer.register(this) { it.cancel() } }

  private val progressFlow = MutableStateFlow<CommitChecksProgressIndicator?>(null)
  private var progress: CommitChecksProgressIndicator? by progressFlow::value

  private val failuresPanel = FailuresPanel()
  private val label = JBLabel().apply { isVisible = false }

  override var isEmptyMessage: Boolean by observable(false) { _, oldValue, newValue ->
    if (oldValue == newValue) return@observable
    update()
  }

  override var isEmptyChanges: Boolean by observable(false) { _, oldValue, newValue ->
    if (oldValue == newValue) return@observable
    update()
  }

  override var isDumbMode: Boolean by observable(false) { _, oldValue, newValue ->
    if (oldValue == newValue) return@observable
    update()
  }

  fun setup(commitWorkflowUi: CommitWorkflowUi, commitMessage: EditorTextComponent) {
    add(label)
    add(failuresPanel)

    Disposer.register(commitWorkflowUi, this)
    commitMessage.addDocumentListener(this)
    commitWorkflowUi.addInclusionListener(this, this)

    setupShowProgressInStatusBar()
    setupProgressVisibilityDelay()
    setupProgressSpinnerTooltip()
  }

  private fun setupShowProgressInStatusBar() =
    Disposer.register(this, UiNotifyConnector(this, object : Activatable {
      override fun showNotify() {
        progress?.let { removeFromStatusBar(it) }
      }

      override fun hideNotify() {
        progress?.let { addToStatusBar(it) }
      }
    }))

  @Suppress("EXPERIMENTAL_API_USAGE")
  private fun setupProgressVisibilityDelay() {
    progressFlow
      .debounce(DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS.toLong())
      .onEach { indicator ->
        if (indicator?.isRunning == true && failuresPanel.isEmpty()) indicator.component.isVisible = true
      }
      .launchIn(scope + CoroutineName("Commit checks indicator visibility"))
  }

  private fun setupProgressSpinnerTooltip() {
    val tooltip = CommitChecksProgressIndicatorTooltip({ progress }, { failuresPanel.width })
    tooltip.installOn(failuresPanel.iconLabel, this)
  }

  override fun dispose() = Unit

  override fun startProgress(): ProgressIndicatorEx {
    check(progress == null) { "Commit checks indicator already created" }

    val indicator = InlineCommitChecksProgressIndicator()
    Disposer.register(this, indicator)

    indicator.component.isVisible = false
    indicator.addStateDelegate(object : AbstractProgressIndicatorExBase() {
      override fun start() = progressStarted()
      override fun stop() = progressStopped()
    })

    progress = indicator
    indicator.start()
    return indicator
  }

  private fun progressStarted() {
    add(progress!!.component)
    // we assume `isShowing == true` here - so we do not need to add progress to status bar
    failuresPanel.clearFailures()
  }

  private fun progressStopped() {
    progress!!.let {
      remove(it.component)
      removeFromStatusBar(it)
      Disposer.dispose(it)
    }
    progress = null

    failuresPanel.endProgress()
  }

  private fun addToStatusBar(progress: CommitChecksProgressIndicator) {
    val frame = WindowManagerEx.getInstanceEx().findFrameFor(null) ?: return
    val statusBar = frame.statusBar as? StatusBarEx ?: return

    statusBar.addProgress(progress, CommitChecksTaskInfo())
  }

  private fun removeFromStatusBar(progress: CommitChecksProgressIndicator) =
    // `finish` tracks list of finished `TaskInfo`-s - so we pass new instance to remove from status bar
    progress.finish(CommitChecksTaskInfo())

  override fun addCommitCheckFailure(text: String, detailsViewer: () -> Unit) {
    progress?.component?.isVisible = false
    failuresPanel.addFailure(CommitCheckFailure(text, detailsViewer))
  }

  override fun clearCommitCheckFailures() = failuresPanel.clearFailures()

  override fun documentChanged(event: DocumentEvent) = clearError()
  override fun inclusionChanged() = clearError()

  protected fun update() {
    val error = buildErrorText()

    when {
      error != null -> label.setError(error)
      isDumbMode -> label.setWarning(message("label.commit.checks.not.available.during.indexing"))
      else -> label.isVisible = false
    }
  }

  protected open fun clearError() {
    isEmptyMessage = false
    isEmptyChanges = false
  }

  @NlsContexts.Label
  protected open fun buildErrorText(): String? =
    when {
      isEmptyChanges && isEmptyMessage -> message("error.no.changes.no.commit.message")
      isEmptyChanges -> message("error.no.changes.to.commit")
      isEmptyMessage -> message("error.no.commit.message")
      else -> null
    }
}

private class CommitCheckFailure(@Nls val text: String, val detailsViewer: () -> Unit)

private class FailuresPanel : BorderLayoutPanel() {
  private var nextFailureId = 0
  private val failures = mutableMapOf<Int, CommitCheckFailure>()

  val iconLabel = JBLabel()
  private val description = createHtmlViewer(true, null, null, null)

  init {
    addToLeft(iconLabel)
    addToCenter(description)

    description.border = emptyLeft(4)
    description.addHyperlinkListener { showDetails(it) }

    isOpaque = false
    isVisible = false
  }

  fun isEmpty(): Boolean = failures.isEmpty()

  fun showDetails(event: HyperlinkEvent) {
    if (event.eventType != HyperlinkEvent.EventType.ACTIVATED) return

    val failure = failures[event.description.toInt()] ?: return
    failure.detailsViewer()
  }

  fun clearFailures() {
    isVisible = false
    iconLabel.icon = null
    failures.clear()
    update()
  }

  fun addFailure(failure: CommitCheckFailure) {
    isVisible = true
    iconLabel.icon = AnimatedIcon.Default()
    failures[nextFailureId++] = failure
    update()
  }

  fun endProgress() {
    isVisible = failures.isNotEmpty()
    if (isVisible) iconLabel.icon = AllIcons.General.Warning
  }

  private fun update() = setHtml(description, buildDescription().toString(), null)

  private fun buildDescription(): HtmlChunk {
    if (failures.isEmpty()) return HtmlChunk.empty()

    val failuresLinks = formatNarrowAndList(failures.map { HtmlChunk.link(it.key.toString(), it.value.text) })
    return HtmlChunk.raw(message("label.commit.checks.failed", failuresLinks))
  }
}