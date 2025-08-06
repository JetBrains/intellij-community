// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.editor.state.ObservableState
import org.intellij.lang.annotations.MagicConstant
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import javax.swing.border.Border

@ApiStatus.Experimental
@ApiStatus.Internal
class EditorState : ObservableState() {
  @Suppress("ConstPropertyName")
  companion object {
    // for compatibility with Java
    const val isInsertModePropertyName = "isInsertMode"
    const val isColumnModePropertyName = "isColumnMode"
    const val isOneLineModePropertyName = "isOneLineMode"
    const val isEmbeddedIntoDialogWrapperPropertyName = "isEmbeddedIntoDialogWrapper"
    const val verticalScrollBarOrientationPropertyName = "verticalScrollBarOrientation"
    const val isStickySelectionPropertyName = "isStickySelection"
    const val myForcedBackgroundPropertyName = "myForcedBackground"
    const val myBorderPropertyName = "myBorder"
    const val disableDefaultSoftWrapsCalculationPropertyName = "disableDefaultSoftWrapsCalculation"
  }


  // modes
  var isViewer: Boolean by property(false)
  var isInsertMode: Boolean by property(true)
  var isColumnMode: Boolean by property(false)
  var isOneLineMode: Boolean by property(false)
  var isRendererMode: Boolean by property(false)

  var isEmbeddedIntoDialogWrapper: Boolean by property(false)

  // actions
  var contextMenuGroupId: String? by property(IdeActions.GROUP_BASIC_EDITOR_POPUP)

  // rendering
  var isUseAntialiasing: Boolean by property(true)

  // scrollbars
  var verticalScrollBarOrientation: Int by property(EditorEx.VERTICAL_SCROLLBAR_RIGHT)
  var isScrollToCaret: Boolean by property(true)

  // selection
  var isPaintSelection: Boolean by property(false)
  var isStickySelection: Boolean by property(false)

  // text
  var horizontalTextAlignment: Int by property(EditorImpl.TEXT_ALIGNMENT_LEFT)

  var myForcedBackground: Color? by property(null)
  var myBorder: Border? by property(null)

  var myPlaceholderText: CharSequence? by property(null)
  var myPlaceholderAttributes: TextAttributes? by property(null)
  var myShowPlaceholderWhenFocused: Boolean by property(false)

  // leading spaces
  var disableDefaultSoftWrapsCalculation: Boolean by property(false)
}