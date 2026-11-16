// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.components

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.ui.components.DropDownLink
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

internal class DropDownLinkPanel : UISandboxPanel {

  override val title: String = "DropDownLink"

  override fun createContent(disposable: Disposable): JComponent {
    val items = listOf(
      DevkitUiDslBundle.message("sandbox.drop.down.link.item.name"),
      DevkitUiDslBundle.message("sandbox.drop.down.link.item.date"),
      DevkitUiDslBundle.message("sandbox.drop.down.link.item.size"),
    )

    return panel {
      row {
        text(DevkitUiDslBundle.message("sandbox.drop.down.link.description"))
      }

      group(DevkitUiDslBundle.message("sandbox.border.title.states")) {
        row {
          text(DevkitUiDslBundle.message("sandbox.drop.down.link.default.note"))
        }
        row(DevkitUiDslBundle.message("sandbox.drop.down.link.label.sort.by")) {
          cell(DropDownLink(items[0], items, onSelect = {}, updateText = true))
        }
        row(DevkitUiDslBundle.message("sandbox.drop.down.link.label.disabled")) {
          cell(DropDownLink(items[0], items, onSelect = {}, updateText = true).apply {
            autoHideOnDisable = false
            isEnabled = false
          })
        }
      }

      group(DevkitUiDslBundle.message("sandbox.drop.down.link.group.ui.dsl")) {
        row {
          dropDownLink(items[0], items)
        }
      }
    }
  }
}
