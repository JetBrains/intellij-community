// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.screenshots.checkbox

import com.intellij.devkit.uiDsl.sandbox.UISandboxScreenshotPanel
import com.intellij.openapi.Disposable
import com.intellij.ui.dsl.builder.panel
import java.awt.Dimension
import javax.swing.JComponent

/**
 * @author Konstantin Bulenkov
 */
internal open class OneSelectedCheckboxPanel(
  correct: Boolean,
  val text: String,
  size: Dimension? = null,
  relativePath: String? = null,
) : UISandboxScreenshotPanel() {

  override val title: String = if (correct) "Correct" else "Incorrect"
  override val screenshotSize: Dimension? = size
  override val sreenshotRelativePath: String? = relativePath

  override fun createContentForScreenshot(disposable: Disposable): JComponent {
    return panel {
      row {
        @Suppress("HardCodedStringLiteral")
        checkBox(text).apply { component.isSelected = true }
      }
    }
  }
}