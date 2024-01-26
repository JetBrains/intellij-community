// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.sandbox.dsl

import com.intellij.internal.ui.sandbox.UISandboxPanel
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
      group(title = "Group at top, no gap before") {
        row {
          group1Visibility = checkBox("Group1 visibility")
            .selected(true)
            .component
          group1Enabled = checkBox("Group1 enabled")
            .selected(true)
            .component
        }
        indent {
          row {
            group1RowVisibility = checkBox("Group1 label1 visibility")
              .selected(true)
              .component
          }
        }
        row {
          group2Visibility = checkBox("Group2 visibility")
            .selected(true)
            .component
        }
      }

      row("A very very long label") {
        textField()
      }

      group(title = "Group1, gaps around") {
        row("label1") {
          textField()
        }.visibleIf(group1RowVisibility.selected)
        row("label2 long") {
          textField()
        }
      }.visibleIf(group1Visibility.selected)
        .enabledIf(group1Enabled.selected)

      groupRowsRange(title = "Group RowsRange title") {
        row("label1") {
          textField()
        }
        row("label2 long") {
          textField()
        }
      }.visibleIf(group2Visibility.selected)
      group {
        row {
          label("Group without title")
        }
      }
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

      row("separator") {}

      collapsibleGroup("CollapsibleGroup") {
        row("Row with label") {}
      }

      row("separator") {}

      group("Group, indent = false, no gaps", indent = false) {
        row("Row with label") {}
      }.topGap(TopGap.NONE)
        .bottomGap(BottomGap.NONE)

      row("separator") {}

      groupRowsRange("GroupRowsRange, indent = false, no gaps", indent = false, topGroupGap = false, bottomGroupGap = false) {
        row("Row with label") {}
      }

      row("separator") {}

      collapsibleGroup("CollapsibleGroup, indent = false, no gaps", indent = false) {
        row("Row with label") {}
      }.topGap(TopGap.NONE)
        .bottomGap(BottomGap.NONE)

      row("separator") {}

      group("Group at bottom, no gap after") {
        row("Row with label") {}
      }
    }
  }
}