// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.gridLayout.GridLayout
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JTextField

class NewModuleStep(context: WizardContext, factory: NewProjectWizardStep.RootStepFactory) : ModuleWizardStep() {

  private val step by lazy { factory.createStep(context) }

  fun setupProject(project: Project) = step.setupProject(project)

  private val panelBuilder = NewProjectWizardPanelBuilder(context)

  override fun validate() = panelBuilder.validate()

  override fun updateDataModel() = panelBuilder.apply()

  override fun getPreferredFocusedComponent() = panelBuilder.preferredFocusedComponent

  override fun updateStep() {
    (preferredFocusedComponent as? JTextField)?.selectAll()
  }

  override fun getComponent() =
    panelBuilder.panel { step.setupUI(this) }
      .apply { withBorder(JBUI.Borders.empty(14, 20)) }
      .also { fixUiShiftingWhenChoosingMultiStep(it) }

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
    return constraints!= null && constraints.x == 0 && constraints.gaps.left == 0
  }

  private fun JComponent.setMinimumWidth(width: Int) {
    minimumSize = minimumSize.apply { this.width = width }
  }
}
