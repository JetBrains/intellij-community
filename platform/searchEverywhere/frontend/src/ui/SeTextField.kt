// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.ui

import com.intellij.accessibility.TextFieldWithListAccessibleContext
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.searchComponents.ExtendableSearchTextField
import org.jetbrains.annotations.ApiStatus.Internal
import javax.accessibility.AccessibleContext

@Internal
open class SeTextField(private val initialText: String?, private val resultListAccessibleContext: () -> AccessibleContext) : ExtendableSearchTextField() {
  var isInitialSearchPattern: Boolean = true
    private set
  private var onTextChanged: (String) -> Unit = {}

  init {
    isOpaque = true
    text = initialText ?: ""

    document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: javax.swing.event.DocumentEvent) {
        onTextChanged(text)
        isInitialSearchPattern = false
      }
    })
  }

  fun configure(lastSearchText: String?, onTextChanged: (String) -> Unit) {
    if (isInitialSearchPattern) {
      if (lastSearchText != null && initialText.isNullOrEmpty()) {
        text = lastSearchText
        selectAll()
      }
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