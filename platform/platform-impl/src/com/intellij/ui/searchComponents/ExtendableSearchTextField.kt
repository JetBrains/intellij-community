// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.searchComponents

import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.util.registry.Registry.Companion.intValue
import com.intellij.openapi.util.registry.Registry.Companion.`is`
import com.intellij.ui.ExperimentalUI.Companion.isNewUI
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.Dimension
import javax.swing.border.Border
import javax.swing.border.EmptyBorder

@Internal
open class ExtendableSearchTextField : ExtendableTextField() {
  init {
    if (isNewUI()) {
      isOpaque = false
      border = PopupUtil.createComplexPopupTextFieldBorder()
    }
    else {
      val empty: Border = EmptyBorder(JBUI.CurrentTheme.BigPopup.searchFieldInsets())
      val topLine = JBUI.Borders.customLine(JBUI.CurrentTheme.BigPopup.searchFieldBorderColor(), 1, 0, 0, 0)
      border = JBUI.Borders.merge(empty, topLine, true)
      background = JBUI.CurrentTheme.BigPopup.searchFieldBackground()
    }
    focusTraversalKeysEnabled = false

    if (`is`("new.search.everywhere.use.editor.font")) {
      val editorFont = EditorUtil.getEditorFont()
      font = editorFont
    }

    val fontDelta = intValue("new.search.everywhere.font.size.delta")
    if (fontDelta != 0) {
      var font = font
      font = font.deriveFont(fontDelta.toFloat() + font.size)
      setFont(font)
    }
  }

  override fun getPreferredSize(): Dimension {
    val size = super.getPreferredSize()
    size.height = Integer.max(scale(29), size.height)
    return size
  }
}