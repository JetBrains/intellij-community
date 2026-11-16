// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.components

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.NonNls
import javax.swing.JComponent

private const val GUIDELINES_URL: @NonNls String = "https://plugins.jetbrains.com/docs/intellij/link.html"

internal class HyperlinkLabelPanel : UISandboxPanel {

  override val title: String = "HyperlinkLabel"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      row {
        text(DevkitUiDslBundle.message("sandbox.hyperlink.label.description"))
      }

      group(DevkitUiDslBundle.message("sandbox.border.title.states.and.variants")) {
        row {
          text(DevkitUiDslBundle.message("sandbox.hyperlink.label.default.note"))
        }
        row(DevkitUiDslBundle.message("sandbox.hyperlink.label.label.url")) {
          cell(createUrlLabel())
        }
        row(DevkitUiDslBundle.message("sandbox.hyperlink.label.label.action")) {
          cell(createActionLabel())
        }
        row(DevkitUiDslBundle.message("sandbox.hyperlink.label.label.icon")) {
          cell(createUrlLabel().apply { setIcon(AllIcons.General.Information) })
        }
        row(DevkitUiDslBundle.message("sandbox.hyperlink.label.label.disabled")) {
          cell(createUrlLabel().apply { isEnabled = false })
        }
      }
    }
  }

  private fun createUrlLabel(): HyperlinkLabel {
    return HyperlinkLabel().apply {
      setHyperlinkText(DevkitUiDslBundle.message("sandbox.hyperlink.label.text.url"))
      setHyperlinkTarget(GUIDELINES_URL)
    }
  }

  private fun createActionLabel(): HyperlinkLabel {
    var clickCount = 0
    val label = HyperlinkLabel()
    label.setHyperlinkText(DevkitUiDslBundle.message("sandbox.hyperlink.label.text.action", clickCount))
    label.addHyperlinkListener {
      clickCount++
      label.setHyperlinkText(DevkitUiDslBundle.message("sandbox.hyperlink.label.text.action", clickCount))
    }
    return label
  }
}
