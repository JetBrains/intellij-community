// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.screenshots.checkbox

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxScreenshotPanel
import com.intellij.openapi.Disposable
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

/**
 * @author Konstantin Bulenkov
 */
internal class WhenToUseCheckboxesPanel : UISandboxScreenshotPanel() {
  override val title: String = "When to use"
  override val screenshotSize = null
  override val sreenshotRelativePath = null

  override fun createContentForScreenshot(disposable: Disposable): JComponent {
    return panel {
      @Suppress("DialogTitleCapitalization")
      buttonsGroup(DevkitUiDslBundle.message("sandbox.label.ui.options")) {
        row { checkBox(DevkitUiDslBundle.message("sandbox.checkbox.smooth.scrolling")).apply { component.isSelected = true } }
        row { checkBox(DevkitUiDslBundle.message("sandbox.checkbox.display.icons.in.menu.items")).apply { component.isSelected = true } }
        row { checkBox(DevkitUiDslBundle.message("sandbox.checkbox.enable.mnemonics.in.menu")) }
      }
    }
  }
}