// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.components

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.dsl.builder.panel
import javax.swing.Icon
import javax.swing.JComponent

internal class LinkLabelPanel : UISandboxPanel {

  override val title: String = "LinkLabel"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      row {
        text(DevkitUiDslBundle.message("sandbox.link.label.page.description"))
      }

      group(DevkitUiDslBundle.message("sandbox.border.title.states.and.variants")) {
        row {
          text(DevkitUiDslBundle.message("sandbox.link.label.page.default.note"))
        }
        row(DevkitUiDslBundle.message("sandbox.link.label.page.label.default")) {
          cell(createLink(icon = null))
        }
        row(DevkitUiDslBundle.message("sandbox.link.label.page.label.icon")) {
          cell(createLink(AllIcons.General.Settings))
        }
        row(DevkitUiDslBundle.message("sandbox.link.label.page.label.disabled")) {
          cell(createLink(icon = null).apply { isEnabled = false })
        }
      }
    }
  }

  private fun createLink(icon: Icon?): LinkLabel<Unit> {
    var clickCount = 0
    val link = LinkLabel<Unit>(DevkitUiDslBundle.message("sandbox.link.label.page.text", clickCount), icon)
    link.setListener({ _, _ ->
      clickCount++
      link.text = DevkitUiDslBundle.message("sandbox.link.label.page.text", clickCount)
    }, null)
    return link
  }
}
