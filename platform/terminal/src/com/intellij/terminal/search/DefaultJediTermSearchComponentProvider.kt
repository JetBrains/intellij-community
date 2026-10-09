// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.search

import com.intellij.ui.SearchTextField
import com.jediterm.terminal.SubstringFinder
import com.jediterm.terminal.ui.JediTermSearchComponent
import com.jediterm.terminal.ui.JediTermSearchComponentListener
import org.jetbrains.annotations.ApiStatus
import java.awt.event.KeyListener
import javax.swing.JComponent
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

@ApiStatus.Internal
class DefaultJediTermSearchComponent : JediTermSearchComponent {
  private val myTextField = SearchTextField(false)

  override fun getComponent(): JComponent {
    myTextField.isOpaque = false
    return myTextField
  }

  private fun searchSettingsChanged(listener: JediTermSearchComponentListener) {
    listener.searchSettingsChanged(myTextField.text, false)
  }

  override fun addListener(listener: JediTermSearchComponentListener) {
    myTextField.addDocumentListener(object : DocumentListener {
      override fun insertUpdate(e: DocumentEvent) {
        searchSettingsChanged(listener)
      }

      override fun removeUpdate(e: DocumentEvent) {
        searchSettingsChanged(listener)
      }

      override fun changedUpdate(e: DocumentEvent) {
        searchSettingsChanged(listener)
      }
    })
  }

  override fun addKeyListener(listener: KeyListener) {
    myTextField.addKeyboardListener(listener)
  }

  override fun onResultUpdated(result: SubstringFinder.FindResult?) {}
}
