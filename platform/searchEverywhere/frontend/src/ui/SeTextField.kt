// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.ui

import com.intellij.accessibility.TextFieldWithListAccessibleContext
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.platform.ide.productMode.IdeProductMode
import com.intellij.platform.searchEverywhere.providers.SeLog
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.searchComponents.ExtendableSearchTextField
import com.intellij.util.ui.EDT
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus.Internal
import javax.accessibility.AccessibleContext
import javax.swing.text.JTextComponent

@Internal
open class SeTextField(private val initialText: String?, private val resultListAccessibleContext: () -> AccessibleContext) : ExtendableSearchTextField() {
  var isInitialSearchPattern: Boolean = true
    private set
  private var onTextChanged: (String) -> Unit = {}

  init {
    isOpaque = true
    background = JBUI.CurrentTheme.Popup.BACKGROUND
    text = initialText ?: ""

    document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: javax.swing.event.DocumentEvent) {
        val docText = text
        SeLog.log(SeLog.PATTERN) { "Got a text change from SeTextField: ${text}" }
        SeLog.log(SeLog.CARET) {
          "SeTextField docChange [${e.type}] offset=${e.offset} len=${e.length}: initial=$isInitialSearchPattern - " + stateLogMessage()
        }
        onTextChanged(docText)
        isInitialSearchPattern = false
      }
    })

    // IJPL-188794 Quick definition popup does not update in RemDev. Disable it in RemDev
    if (IdeProductMode.isFrontend) {
      val actionQuickImplementations = ActionManager.getInstance().getAction(IdeActions.ACTION_QUICK_IMPLEMENTATIONS)
      val emptyAction = object : AnAction() {
        override fun actionPerformed(e: AnActionEvent) {
          // do nothing
        }
      }
      emptyAction.registerCustomShortcutSet(actionQuickImplementations.shortcutSet, this)
    }
  }

  fun configure(lastSearchText: String?, onTextChanged: (String) -> Unit) {
    SeLog.log(SeLog.CARET) {
      "SeTextField.configure: initial=$isInitialSearchPattern, lastSearchText='$lastSearchText', initialText='$initialText' - " +
      stateLogMessage()
    }

    if (isInitialSearchPattern) {
      // The user has not typed since construction, so the field still holds `initialText`.
      // Seeding from the last search/history here cannot clobber user input.
      if (lastSearchText != null && initialText.isNullOrEmpty()) {
        setText(lastSearchText, selectAll = true, reason = "configure-seed")
      }
      else {
        selectAll()
        SeLog.log(SeLog.CARET) { "SeTextField.configure selectAll - " + stateLogMessage() }
      }
    }
    else {
      // The user already started typing (possibly during the idle popup, before the VM was wired).
      // Never overwrite their input: just flush the current text to the VM.
      SeLog.log(SeLog.CARET) { "SeTextField.configure flushing user-typed text to VM: '$text'" }
      onTextChanged(text)
    }

    this.onTextChanged = onTextChanged
  }

  fun setText(newText: String, selectAll: Boolean, reason: String) {
    val onEdt = EDT.isCurrentThreadEdt()

    SeLog.log(SeLog.CARET) {
      "SeTextField.setText [$reason]: initial=$isInitialSearchPattern, newText=$newText, selectAll=$selectAll edt=$onEdt - " +
      stateLogMessage()
    }

    text = newText

    if (selectAll) selectAll()
    else caretPosition = newText.length

    SeLog.log(SeLog.CARET) { "SeTextField.setText [$reason] done - ${stateLogMessage()}" }
    if (!onEdt) SeLog.warn("SeTextField.setText [$reason] called off-EDT; caret may jump (newText='$newText')")
  }

  override fun getAccessibleContext(): AccessibleContext? {
    if (accessibleContext == null) {
      accessibleContext = TextFieldWithListAccessibleContext(this, resultListAccessibleContext())
    }
    return accessibleContext
  }
}

@Internal
fun JTextComponent.stateLogMessage(): String = "[text=$text, caret=$caretPosition, selection=[$selectionStart,$selectionEnd]]"
