// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.listCellRenderer.impl

import com.intellij.ui.ExperimentalUI
import com.intellij.ui.dsl.listCellRenderer.RowParams
import com.intellij.ui.popup.list.SelectablePanel
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Component
import javax.swing.border.Border

@ApiStatus.Internal
internal class RowParamsImpl(private val selectablePanel: SelectablePanel) : RowParams {

  override var border: Border?
    get() = selectablePanel.border
    set(value) {
      selectablePanel.border = value
    }

  override var background: Color?
    get() = if (ExperimentalUI.isNewUI()) selectablePanel.selectionColor else selectablePanel.background
    set(value) {
      if (ExperimentalUI.isNewUI()) {
        selectablePanel.selectionColor = value
      }
      else {
        selectablePanel.background = value
      }
    }

  override var accessibleContextProvider: Component?
    get() = selectablePanel.accessibleContextProvider
    set(value) {
      selectablePanel.accessibleContextProvider = value
    }
}