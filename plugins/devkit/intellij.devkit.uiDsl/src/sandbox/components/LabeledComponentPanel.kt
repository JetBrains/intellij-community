// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.components

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.LabeledComponent
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.LabelPosition
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import javax.swing.JComponent

private const val COLUMNS = 20

internal class LabeledComponentPanel : UISandboxPanel {

  override val title: String = "LabeledComponent"

  override fun createContent(disposable: Disposable): JComponent {
    val left = createComponent(DevkitUiDslBundle.message("sandbox.labeled.component.label.left"), BorderLayout.WEST, isEnabled = true)
    val leftDisabled = createComponent(DevkitUiDslBundle.message("sandbox.labeled.component.label.left.disabled"), BorderLayout.WEST,
                                       isEnabled = false)
    UIUtil.mergeComponentsWithAnchor(left, leftDisabled)
    val label = DevkitUiDslBundle.message("sandbox.labeled.component.text.label")

    return panel {
      row {
        text(DevkitUiDslBundle.message("sandbox.labeled.component.description"))
      }

      group(DevkitUiDslBundle.message("sandbox.border.title.states.and.variants")) {
        row {
          cell(createComponent(DevkitUiDslBundle.message("sandbox.labeled.component.label.top"), BorderLayout.NORTH, isEnabled = true))
        }
        row {
          cell(createComponent(DevkitUiDslBundle.message("sandbox.labeled.component.label.top.disabled"), BorderLayout.NORTH, isEnabled = false))
        }.topGap(TopGap.SMALL)
        row {
          cell(left)
        }.topGap(TopGap.MEDIUM)
        row {
          cell(leftDisabled)
        }
      }

      group(DevkitUiDslBundle.message("sandbox.labeled.component.group.ui.dsl")) {
        row {
          text(DevkitUiDslBundle.message("sandbox.labeled.component.ui.dsl.note"))
        }
        row {
          textField()
            .columns(COLUMNS)
            .label(label, LabelPosition.TOP)
        }
        row(label) {
          textField()
            .columns(COLUMNS)
        }
      }
    }
  }

  private fun createComponent(text: @NlsContexts.Label String, labelLocation: String, isEnabled: Boolean): LabeledComponent<JBTextField> {
    return LabeledComponent.create(JBTextField(COLUMNS), text, labelLocation).apply {
      this.isEnabled = isEnabled
    }
  }
}
