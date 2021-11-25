// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.DslComponentProperty
import com.intellij.ui.dsl.gridLayout.GridLayout
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import javax.swing.JComponent
import javax.swing.JLabel

class NewProjectWizardStepPanel(val step: NewProjectWizardStep) {
  private val builder = NewProjectWizardPanelBuilder(step.context)

  fun getPreferredFocusedComponent() = builder.getPreferredFocusedComponent()

  fun validate() = builder.validate()

  fun isModified() = builder.isModified()

  fun apply() = builder.apply()

  fun reset() = builder.reset()

  val component by lazy {
    builder.panel(step::setupUI)
      .apply { withBorder(JBUI.Borders.empty(14, 20)) }
      .also { fixUiShiftingWhenChoosingMultiStep(it) }
  }

  private fun fixUiShiftingWhenChoosingMultiStep(panel: DialogPanel) {
    val labels = UIUtil.uiTraverser(panel)
      .filterIsInstance<JLabel>()
      .filter { isRowLabel(it) }
    val width = labels.maxOf { it.preferredSize.width }
    labels.forEach { it.setMinimumWidth(width) }
  }

  private fun isRowLabel(label: JLabel): Boolean {
    val layout = (label.parent as? DialogPanel)?.layout as? GridLayout
    if (layout == null) {
      return false
    }
    val constraints = layout.getConstraints(label)
    return label.getClientProperty(DslComponentProperty.ROW_LABEL) == true && constraints != null && constraints.gaps.left == 0
  }

  private fun JComponent.setMinimumWidth(width: Int) {
    minimumSize = minimumSize.apply { this.width = width }
  }
}