// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:ApiStatus.Internal

package com.intellij.devkit.uiDsl.sandbox.tests.dsl.listCellRenderer

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.devkit.uiDsl.sandbox.intList
import com.intellij.devkit.uiDsl.sandbox.jbList
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.intellij.ui.dsl.listCellRenderer.LcrInitParams
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.ui.layout.selected
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import javax.swing.JCheckBox
import javax.swing.JComponent

internal class LcrListTestPanel : UISandboxPanel {

  override val title: String = "List"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      lateinit var enabled: JCheckBox
      row {
        enabled = checkBox(DevkitUiDslBundle.message("sandbox.checkbox.enabled"))
          .selected(true)
          .component
      }

      indent {
        row {
          val aligns = listOf(LcrInitParams.Align.LEFT, LcrInitParams.Align.CENTER, LcrInitParams.Align.RIGHT)
          jbList(DevkitUiDslBundle.message("sandbox.label.align"), intList(), listCellRenderer {
            val customAlign = aligns.getOrNull(index % (aligns.size + 1))
            text("$value: $customAlign") {
              align = customAlign
            }
          }).align(Align.FILL)
        }

        row {
          val colors = listOf(UIUtil.getLabelForeground(),
                              JBColor.GREEN,
                              JBColor.MAGENTA)
          val styles = listOf(SimpleTextAttributes.STYLE_PLAIN,
                              SimpleTextAttributes.STYLE_BOLD,
                              SimpleTextAttributes.STYLE_ITALIC)

          jbList(DevkitUiDslBundle.message("sandbox.label.foreground"), intList(), listCellRenderer {
            val i = index % colors.size
            text(DevkitUiDslBundle.message("sandbox.item.0", value)) {
              if (i > 0) {
                foreground = colors[i]
              }
            }
          })

          jbList(DevkitUiDslBundle.message("sandbox.label.attributes"), intList(), listCellRenderer {
            val i = index % colors.size
            text(DevkitUiDslBundle.message("sandbox.item.0", value)) {
              attributes = SimpleTextAttributes(styles[i], colors[i])
            }
          })

          jbList(DevkitUiDslBundle.message("sandbox.label.background"), intList(), listCellRenderer {
            val i = index % colors.size
            if (i > 0) {
              background = colors[i]
            }
            text(DevkitUiDslBundle.message("sandbox.item.0", value))
          })
        }

        row {
          jbList("FixedCellHeight", intList(), listCellRenderer {
            icon(if (index % 2 == 0) AllIcons.General.Add else AllIcons.General.Gear)
            text(DevkitUiDslBundle.message("sandbox.item.0", value))
          }, patchList = { it.fixedCellHeight = JBUIScale.scale(30) })
          jbList(DevkitUiDslBundle.message("sandbox.label.big.font"), intList(), listCellRenderer {
            icon(if (index % 2 == 0) AllIcons.General.Add else AllIcons.General.Gear)
            text(DevkitUiDslBundle.message("sandbox.item.0", value)) {
              font = JBFont.h1()
            }
            switch(index % 2 == 0)
            text(DevkitUiDslBundle.message("sandbox.small.comment")) {
              font = JBFont.small()
              foreground = greyForeground
            }
          }, patchList = { it.fixedCellHeight = JBUIScale.scale(30) })
        }
      }.enabledIf(enabled.selected)
    }
  }
}
