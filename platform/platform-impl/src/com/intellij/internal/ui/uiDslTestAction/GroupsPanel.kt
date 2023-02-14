// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.ui.uiDslTestAction

import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.*
import org.jetbrains.annotations.ApiStatus

@Suppress("DialogTitleCapitalization")
@ApiStatus.Internal
internal class GroupsPanel {

  val panel = panel {
    lateinit var group1Visibility: JBCheckBox
    lateinit var group1Enabled: JBCheckBox
    lateinit var group1RowVisibility: JBCheckBox
    lateinit var group2Visibility: JBCheckBox
    group(title = "Group at top, no gap before") {
            row {
              group1Visibility = checkBox("Group1 visibility")
                .applyToComponent {
                  isSelected = true
                }.component
              group1Enabled = checkBox("Group1 enabled")
                .applyToComponent {
                  isSelected = true
                }.component
            }
            indent {
              row {
                group1RowVisibility = checkBox("Group1 label1 visibility")
                  .applyToComponent {
                    isSelected = true
                  }.component
              }
            }
            row {
              group2Visibility = checkBox("Group2 visibility")
                .applyToComponent {
                  isSelected = true
                }.component
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
