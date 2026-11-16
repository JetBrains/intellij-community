// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.components

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.SegmentedButton
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

private const val MAX_BUTTONS_COUNT = 4

internal class SegmentedButtonComponentPanel : UISandboxPanel {

  override val title: String = "SegmentedButton"

  override fun createContent(disposable: Disposable): JComponent {
    val options = listOf(
      DevkitUiDslBundle.message("sandbox.segmented.button.page.option.list"),
      DevkitUiDslBundle.message("sandbox.segmented.button.page.option.tree"),
      DevkitUiDslBundle.message("sandbox.segmented.button.page.option.table"),
      DevkitUiDslBundle.message("sandbox.segmented.button.page.option.grid"),
      DevkitUiDslBundle.message("sandbox.segmented.button.page.option.cards"),
    )
    val fewOptions = options.take(3)

    return panel {
      row {
        text(DevkitUiDslBundle.message("sandbox.segmented.button.page.description"))
      }

      group(DevkitUiDslBundle.message("sandbox.border.title.states")) {
        row {
          text(DevkitUiDslBundle.message("sandbox.segmented.button.page.default.note"))
        }
        row(DevkitUiDslBundle.message("sandbox.segmented.button.page.label.default")) {
          optionsButton(fewOptions)
        }
        row(DevkitUiDslBundle.message("sandbox.segmented.button.page.label.disabled.item")) {
          optionsButton(fewOptions, disabledOption = fewOptions.last())
        }
        row(DevkitUiDslBundle.message("sandbox.segmented.button.page.label.disabled")) {
          optionsButton(fewOptions)
        }.enabled(false)
      }

      group(DevkitUiDslBundle.message("sandbox.segmented.button.page.group.combo.box")) {
        row {
          text(DevkitUiDslBundle.message("sandbox.segmented.button.page.combo.box.note"))
        }
        row(DevkitUiDslBundle.message("sandbox.segmented.button.page.label.many")) {
          optionsButton(options)
            .maxButtonsCount(MAX_BUTTONS_COUNT)
        }
      }
    }
  }

  private fun Row.optionsButton(options: List<String>, disabledOption: String? = null): SegmentedButton<String> {
    return segmentedButton(options) {
      text = it
      enabled = it != disabledOption
    }.apply { selectedItem = options.first() }
  }
}
