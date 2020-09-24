// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.changes.InclusionListener
import com.intellij.ui.EditorTextComponent
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.UIUtil.getErrorForeground
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

class ChangesViewCommitProgressPanel(private val commitWorkflowUi: ChangesViewCommitWorkflowUi, commitMessage: EditorTextComponent) :
  NonOpaquePanel(VerticalLayout(0)),
  CommitProgressUi,
  InclusionListener,
  DocumentListener {

  private var oldInclusion: Set<Any> = emptySet()

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

  init {
    add(label)

    commitMessage.addDocumentListener(this)
    commitWorkflowUi.addInclusionListener(this, commitWorkflowUi)
  }

  override fun documentChanged(event: DocumentEvent) = clearError()

  override fun inclusionChanged() {
    val newInclusion = commitWorkflowUi.inclusionModel?.getInclusion().orEmpty()

    if (oldInclusion != newInclusion) clearError()
    oldInclusion = newInclusion
  }

  private fun update() {
    val error = buildErrorText()

    when {
      error != null -> label.setError(error)
      isDumbMode -> label.setWarning(message("label.commit.checks.not.available.during.indexing"))
      else -> label.isVisible = false
    }
  }

  private fun clearError() {
    isEmptyMessage = false
    isEmptyChanges = false
  }

  @NlsContexts.Label
  private fun buildErrorText(): String? =
    when {
      isEmptyChanges && isEmptyMessage -> message("error.no.changes.no.commit.message")
      isEmptyChanges -> message("error.no.changes.to.commit")
      isEmptyMessage -> message("error.no.commit.message")
      else -> null
    }
}