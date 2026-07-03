// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.tests.dsl

import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

internal class ResizableRowsPanel : UISandboxPanel {

  override val title: String = "Resizable Rows"

  override val isScrollbarNeeded: Boolean = false

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      for (rowLayout in RowLayout.entries) {
        row(rowLayout.name) {
          textArea()
            .align(Align.FILL)
        }.layout(rowLayout)
          .resizableRow()
      }
    }
  }
}