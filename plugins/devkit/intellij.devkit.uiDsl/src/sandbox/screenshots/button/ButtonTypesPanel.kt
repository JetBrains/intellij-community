// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DialogTitleCapitalization")

package com.intellij.devkit.uiDsl.sandbox.screenshots.button

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxScreenshotPanel
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.openapi.Disposable
import com.intellij.ui.dsl.builder.panel
import javax.swing.JButton
import javax.swing.JComponent

/**
 * @author Konstantin Bulenkov
 */
internal class ButtonTypesPanel : UISandboxScreenshotPanel() {

  override val title: String = "Button types"
  override val screenshotSize = 1412 x 280
  override val sreenshotRelativePath = "images/ui/button/button_example.png"

  override fun createContentForScreenshot(disposable: Disposable): JComponent {
    return panel {
      row {
        button(DevkitUiDslBundle.message("sandbox.button.primary")) {}
          .applyToComponent {
            putClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY, true)
          }
        button(DevkitUiDslBundle.message("sandbox.button.secondary")) {}
        cell(object : JButton(DevkitUiDslBundle.message("sandbox.focused")) {
          init { isOpaque = false }
          override fun hasFocus() = true
        })
        button(DevkitUiDslBundle.message("sandbox.button.disabled")) {}
          .applyToComponent { isEnabled = false }
      }
    }
  }
}
