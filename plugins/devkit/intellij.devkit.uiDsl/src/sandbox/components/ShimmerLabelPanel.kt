// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.components

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.components.ShimmerLabel
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.panel
import javax.swing.Icon
import javax.swing.JComponent

internal class ShimmerLabelPanel : UISandboxPanel {

  override val title: String = "ShimmerLabel"

  override fun createContent(disposable: Disposable): JComponent {
    val labels = mutableListOf<ShimmerLabel>()

    fun Row.shimmerLabel(text: @NlsSafe String, icon: Icon? = null): Cell<ShimmerLabel> {
      return cell(ShimmerLabel(text, icon).apply {
        isShimmering = true
        labels.add(this)
      })
    }

    return panel {
      row {
        checkBox(DevkitUiDslBundle.message("sandbox.checkbox.shimmering"))
          .applyToComponent { isSelected = true }
          .onChanged { checkBox ->
            for (label in labels) {
              label.isShimmering = checkBox.isSelected
            }
          }
      }

      group(DevkitUiDslBundle.message("sandbox.border.title.examples")) {
        row {
          shimmerLabel("Analyzing project structure…")
        }
        row {
          shimmerLabel("./gradlew clean build --parallel --console=plain", AllIcons.Nodes.Console)
        }
      }
    }
  }
}
