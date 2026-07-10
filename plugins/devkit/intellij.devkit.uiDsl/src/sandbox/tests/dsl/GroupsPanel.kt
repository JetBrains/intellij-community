// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.tests.dsl

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.intellij.ui.layout.selected
import javax.swing.JComponent

@Suppress("DialogTitleCapitalization")
internal class GroupsPanel : UISandboxPanel {

  override val title: String = "Groups"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      lateinit var group1Visibility: JBCheckBox
      lateinit var group1Enabled: JBCheckBox
      lateinit var group1RowVisibility: JBCheckBox
      lateinit var group2Visibility: JBCheckBox
      group(title = DevkitUiDslBundle.message("sandbox.border.title.group.at.top.no.gap.before")) {
        row {
          group1Visibility = checkBox(DevkitUiDslBundle.message("sandbox.checkbox.group1.visibility"))
            .selected(true)
            .component
          group1Enabled = checkBox(DevkitUiDslBundle.message("sandbox.checkbox.group1.enabled"))
            .selected(true)
            .component
        }
        indent {
          row {
            group1RowVisibility = checkBox(DevkitUiDslBundle.message("sandbox.checkbox.group1.label1.visibility"))
              .selected(true)
              .component
          }
        }
        row {
          group2Visibility = checkBox(DevkitUiDslBundle.message("sandbox.checkbox.group2.visibility"))
            .selected(true)
            .component
        }
      }

      row(DevkitUiDslBundle.message("sandbox.a.very.very.long.label")) {
        textField()
      }

      group(title = DevkitUiDslBundle.message("sandbox.border.title.group1.gaps.around")) {
        @Suppress("HardCodedStringLiteral")
        row("label1") {
          textField()
        }.visibleIf(group1RowVisibility.selected)
        @Suppress("HardCodedStringLiteral")
        row("label2 long") {
          textField()
        }
      }.visibleIf(group1Visibility.selected)
        .enabledIf(group1Enabled.selected)

      groupRowsRange(title = DevkitUiDslBundle.message("sandbox.border.title.group.rowsrange.title")) {
        @Suppress("HardCodedStringLiteral")
        row("label1") {
          textField()
        }
        @Suppress("HardCodedStringLiteral")
        row("label2 long") {
          textField()
        }
      }.visibleIf(group2Visibility.selected)
      group {
        row {
          label(DevkitUiDslBundle.message("sandbox.label.group.without.title"))
        }
      }
      @Suppress("HardCodedStringLiteral")
      panel {
        row {
          label("Panel")
        }
        row("label1") {
          textField()
        }
        row("label2 long") {
          textField()
        }
      }

      @Suppress("HardCodedStringLiteral")
      row("separator") {}

      collapsibleGroup("CollapsibleGroup") {
        row(DevkitUiDslBundle.message("sandbox.row.with.label")) {}
      }

      @Suppress("HardCodedStringLiteral")
      row("separator") {}

      @Suppress("HardCodedStringLiteral")
      group("Group, indent = false, no gaps", indent = false) {
        row(DevkitUiDslBundle.message("sandbox.row.with.label")) {}
      }.topGap(TopGap.NONE)
        .bottomGap(BottomGap.NONE)

      @Suppress("HardCodedStringLiteral")
      row("separator") {}

      @Suppress("HardCodedStringLiteral")
      groupRowsRange("GroupRowsRange, indent = false, no gaps", indent = false, topGroupGap = false, bottomGroupGap = false) {
        row(DevkitUiDslBundle.message("sandbox.row.with.label")) {}
      }

      @Suppress("HardCodedStringLiteral")
      row("separator") {}

      @Suppress("HardCodedStringLiteral")
      collapsibleGroup("CollapsibleGroup, indent = false, no gaps", indent = false) {
        row(DevkitUiDslBundle.message("sandbox.row.with.label")) {}
      }.topGap(TopGap.NONE)
        .bottomGap(BottomGap.NONE)

      @Suppress("HardCodedStringLiteral")
      row("separator") {}

      group(DevkitUiDslBundle.message("sandbox.border.title.group.at.bottom.no.gap.after")) {
        row(DevkitUiDslBundle.message("sandbox.row.with.label")) {}
      }
    }
  }
}