// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.components

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.UIUtil
import javax.swing.JComponent

internal class JBLabelPanel : UISandboxPanel {

  override val title: String = "JBLabel"

  override fun createContent(disposable: Disposable): JComponent {
    val text = DevkitUiDslBundle.message("sandbox.jb.label.text")

    return panel {
      row {
        text(DevkitUiDslBundle.message("sandbox.jb.label.description"))
      }

      group(DevkitUiDslBundle.message("sandbox.border.title.states.and.variants")) {
        row {
          text(DevkitUiDslBundle.message("sandbox.jb.label.default.note"))
        }
        row(DevkitUiDslBundle.message("sandbox.jb.label.label.default")) {
          cell(JBLabel(text))
        }
        row(DevkitUiDslBundle.message("sandbox.jb.label.label.disabled")) {
          cell(JBLabel(text).apply { isEnabled = false })
        }
        row(DevkitUiDslBundle.message("sandbox.jb.label.label.icon")) {
          cell(JBLabel(text, AllIcons.General.Information, JBLabel.LEADING))
        }
        row(DevkitUiDslBundle.message("sandbox.jb.label.label.large")) {
          cell(JBLabel(text, UIUtil.ComponentStyle.LARGE))
        }
        row(DevkitUiDslBundle.message("sandbox.jb.label.label.small")) {
          cell(JBLabel(text, UIUtil.ComponentStyle.SMALL))
        }
        row(DevkitUiDslBundle.message("sandbox.jb.label.label.mini")) {
          cell(JBLabel(text, UIUtil.ComponentStyle.MINI))
        }
        row(DevkitUiDslBundle.message("sandbox.jb.label.label.brighter")) {
          cell(JBLabel(text, UIUtil.ComponentStyle.REGULAR, UIUtil.FontColor.BRIGHTER))
        }
        row(DevkitUiDslBundle.message("sandbox.jb.label.label.copyable")) {
          cell(JBLabel(text).setCopyable(true))
        }
      }
    }
  }
}
