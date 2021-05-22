// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.icons.AllIcons
import com.intellij.ide.nls.NlsMessages.formatNarrowAndList
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.changes.InclusionListener
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
import org.jetbrains.annotations.Nls
import javax.swing.JProgressBar
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

open class CommitProgressPanel : NonOpaquePanel(VerticalLayout(4)), CommitProgressUi, InclusionListener, DocumentListener {
  private val progress = JProgressBar().apply {
    isVisible = false
    isIndeterminate = true
  }
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
    add(progress)
    add(failuresPanel)

    commitMessage.addDocumentListener(this)
    commitWorkflowUi.addInclusionListener(this, commitWorkflowUi)
  }

  override fun startProgress() {
    progress.isVisible = true
    failuresPanel.clearFailures()
  }

  override fun addCommitCheckFailure(text: String, detailsViewer: () -> Unit) {
    progress.isVisible = false
    failuresPanel.addFailure(CommitCheckFailure(text, detailsViewer))
  }

  override fun clearCommitCheckFailures() = failuresPanel.clearFailures()

  override fun endProgress() {
    progress.isVisible = false
    failuresPanel.endProgress()
  }

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