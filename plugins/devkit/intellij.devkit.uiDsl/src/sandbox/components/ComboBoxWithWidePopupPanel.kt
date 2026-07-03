// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.components

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.devkit.uiDsl.sandbox.items
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.ComboBoxWithWidePopup
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import java.util.Vector
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent

internal class ComboBoxWithWidePopupPanel : UISandboxPanel {

  override val title: String = "ComboBoxWithWidePopup"

  override fun createContent(disposable: Disposable): JComponent {
    val result = panel {
      row(DevkitUiDslBundle.message("sandbox.empty")) {
        cbWidePopup(emptyList<String>())
      }
      row(DevkitUiDslBundle.message("sandbox.short.items.5")) {
        cbWidePopup(items(5))
      }
      row(DevkitUiDslBundle.message("sandbox.short.items.20")) {
        cbWidePopup(items(20))
      }
      row(DevkitUiDslBundle.message("sandbox.long.items")) {
        cbWidePopup(items(10, "Long items ".repeat(5)))
      }
    }

    return result
  }

  private fun <T> Row.cbWidePopup(items: List<T>): Cell<ComboBoxWithWidePopup<T>> {
    return cell(ComboBoxWithWidePopup(DefaultComboBoxModel(Vector(items)))).applyToComponent {
      renderer = textListCellRenderer("") { it.toString() }
    }
  }
}