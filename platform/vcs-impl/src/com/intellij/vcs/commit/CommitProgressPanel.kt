// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.icons.AllIcons
import com.intellij.ide.nls.NlsMessages.formatNarrowAndList
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.TaskInfo
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.changes.InclusionListener
import com.intellij.openapi.wm.impl.status.InlineProgressIndicator
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.EditorTextComponent
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI.Borders.emptyLeft
import com.intellij.util.ui.JBUI.Borders.emptyTop
import com.intellij.util.ui.SwingHelper.createHtmlViewer
import com.intellij.util.ui.SwingHelper.setHtml
import com.intellij.util.ui.UIUtil.getErrorForeground
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import javax.swing.JPanel
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
  private var progress: CommitChecksProgressIndicator? = null
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
  }

  override fun dispose() = Unit

  override fun startProgress(): ProgressIndicator {
    check(progress == null) { "Commit checks indicator already created" }

    val indicator = CommitChecksProgressIndicator()
    Disposer.register(this, indicator)

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
    failuresPanel.clearFailures()
  }

  private fun progressStopped() {
    remove(progress!!.component)
    Disposer.dispose(progress!!)
    progress = null

    failuresPanel.endProgress()
  }

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

private class CommitChecksProgressIndicator : InlineProgressIndicator(true, CommitChecksTaskInfo) {
  init {
    component.toolTipText = null
  }

  override fun createCompactTextAndProgress(component: JPanel) {
    val textPanel = NonOpaquePanel(BorderLayout())
    textPanel.border = emptyTop(5)
    textPanel.add(myText, BorderLayout.CENTER)

    component.add(myProgress, BorderLayout.CENTER)
    component.add(textPanel, BorderLayout.SOUTH)

    myText.recomputeSize()
  }
}

private object CommitChecksTaskInfo : TaskInfo {
  override fun getTitle(): String = ""
  override fun getCancelText(): String = ""
  override fun getCancelTooltipText(): String = ""
  override fun isCancellable(): Boolean = false
}

private class CommitCheckFailure(@Nls val text: String, val detailsViewer: () -> Unit)

private class FailuresPanel : BorderLayoutPanel() {
  private var nextFailureId = 0
  private val failures = mutableMapOf<Int, CommitCheckFailure>()

  private val iconLabel = JBLabel()
  private val description = createHtmlViewer(true, null, null, null)

  init {
    addToLeft(iconLabel)
    addToCenter(description)

    description.border = emptyLeft(4)
    description.addHyperlinkListener { showDetails(it) }

    isOpaque = false
    isVisible = false
  }

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