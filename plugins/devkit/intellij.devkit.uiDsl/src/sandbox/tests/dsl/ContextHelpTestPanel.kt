// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.tests.dsl

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.plus
import com.intellij.ui.dsl.builder.selected
import com.intellij.ui.layout.selected
import javax.swing.JCheckBox
import javax.swing.JComponent

internal class ContextHelpTestPanel : UISandboxPanel {

  override val title: String = "Context Help"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      row {
        textField()
          .align(AlignX.CENTER)
          .contextHelp("AlignX.CENTER")
      }
      row {
        textField()
          .align(AlignX.RIGHT)
          .commentRight(DevkitUiDslBundle.message("sandbox.text.comment.right"))
          .contextHelp("AlignX.RIGHT")
      }
      group(DevkitUiDslBundle.message("sandbox.border.title.test.enabled.visible")) {
        lateinit var cbEnabled: JCheckBox
        lateinit var cbVisible: JCheckBox
        row {
          cbEnabled = checkBox(DevkitUiDslBundle.message("sandbox.checkbox.enabled"))
            .selected(true)
            .component
          cbVisible = checkBox(DevkitUiDslBundle.message("sandbox.checkbox.visible"))
            .selected(true)
            .component
        }
        row {
          @Suppress("HardCodedStringLiteral")
          textField()
            .align(AlignX.FILL)
            .comment(DevkitUiDslBundle.message("sandbox.text.ordinary.comment"))
            .commentRight(DevkitUiDslBundle.message("sandbox.text.comment.right"))
            .contextHelp("AlignX.FILL, <b>bold</b> text", "Title <b>BOLD</b>")
            .enabledIf(cbEnabled.selected)
            .visibleIf(cbVisible.selected)
        }
      }
      row {
        @Suppress("HardCodedStringLiteral")
        textField()
          .align(Align.CENTER)
          .commentRight(DevkitUiDslBundle.message("sandbox.text.comment.right"))
          .contextHelp("Align.CENTER + resizableRow")
      }.resizableRow()
      row {
        @Suppress("HardCodedStringLiteral")
        textField()
          .align(AlignX.FILL + AlignY.BOTTOM)
          .comment(DevkitUiDslBundle.message("sandbox.text.ordinary.comment"))
          .commentRight(DevkitUiDslBundle.message("sandbox.text.comment.right"))
          .contextHelp("AlignX.RIGHT + AlignY.BOTTOM + resizableRow<br>second line<br>third line")
      }.resizableRow()
    }
  }
}