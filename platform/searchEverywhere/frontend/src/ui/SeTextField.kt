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
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus.Internal
import javax.accessibility.AccessibleContext

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
    if (isInitialSearchPattern) {
      if (lastSearchText != null && initialText.isNullOrEmpty()) {
        text = lastSearchText
      }
      selectAll()
    }
    else {
      onTextChanged(text)
    }

    this.onTextChanged = onTextChanged
  }

  override fun getAccessibleContext(): AccessibleContext? {
    if (accessibleContext == null) {
      accessibleContext = TextFieldWithListAccessibleContext(this, resultListAccessibleContext())
    }
    return accessibleContext
  }
}