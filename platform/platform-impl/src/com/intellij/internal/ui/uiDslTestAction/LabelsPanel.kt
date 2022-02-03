// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.ui.uiDslTestAction

import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.ApiStatus

@Suppress("DialogTitleCapitalization")
@ApiStatus.Internal
internal class LabelsPanel {

  val panel = panel {
    row("Row1:") {
      textField()
    }

    group("RadioButton") {
      buttonsGroup {
        row("Row1:") {
          radioButton("radioButton")
        }
      }
      row("Row2:") {
        textField()
      }
    }

    group("CheckBox") {
      row("Row1:") {
        checkBox("checkBox")
      }
      row("Row2:") {
        textField()
      }
    }

    group("CheckBox") {
      row("Row1:") {
        checkBox("checkBox")
      }
      row("Row2 Long label:") {
        textField()
      }
    }

    group("layout(RowLayout.INDEPENDENT)") {
      row("Row1:") {
        checkBox("checkBox")
      }.layout(RowLayout.INDEPENDENT)
      row("Row2:") {
        textField()
      }.layout(RowLayout.INDEPENDENT)
    }

    group("Cell.label") {
      row {
        checkBox("checkBox")
          .label("Row1:")
      }
      row {
        textField()
          .label("Row2:")
      }
    }
  }
}
