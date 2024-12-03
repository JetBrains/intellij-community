// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit.message

import com.intellij.uiDesigner.core.GridConstraints
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

@ApiStatus.Internal
class CommitMessageInspectionDetailsPanel(
  severityPanel: JComponent,
  optionsPanel: JComponent?
) {
  private var mySeverityChooserPanel: JPanel? = null
  private var myMainPanel: JPanel? = null

  init {
    mySeverityChooserPanel!!.add(severityPanel, BorderLayout.CENTER)
    if (optionsPanel != null) {
      myMainPanel!!.add(optionsPanel, createOptionsPanelConstraints())
    }
  }

  val component: JComponent
    get() = myMainPanel

  companion object {
    private fun createOptionsPanelConstraints(): GridConstraints {
      val result = GridConstraints()

      result.setRow(1)
      result.setColumn(0)
      result.setRowSpan(1)
      result.setColSpan(2)
      result.setAnchor(GridConstraints.ANCHOR_NORTHWEST)
      result.setUseParentLayout(true)

      return result
    }
  }
}
