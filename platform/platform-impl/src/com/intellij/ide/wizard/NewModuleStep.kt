// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.layout.migLayout.patched.*
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import net.miginfocom.layout.BoundSize
import net.miginfocom.layout.CC
import javax.swing.JComponent
import javax.swing.JLabel

class NewModuleStep(context: WizardContext, factory: NewProjectWizardStep.Factory) : ModuleWizardStep() {

  private val step by lazy { factory.createStep(context) }

  private val panelBuilder = NewProjectWizardPanelBuilder(context)

  override fun validate() = panelBuilder.validate()

  override fun updateDataModel() = panelBuilder.apply()

  override fun getPreferredFocusedComponent() = panelBuilder.preferredFocusedComponent

  override fun getComponent() =
    panelBuilder.panel { step.setupUI(this) }
      .apply { withBorder(JBUI.Borders.empty(20, 20)) }
      .also { fixUiShiftingWhenChoosingMultiStep(it) }

  private fun fixUiShiftingWhenChoosingMultiStep(panel: DialogPanel) {
    /* todo fix it
    val labels = UIUtil.uiTraverser(panel)
      .filterIsInstance<JLabel>()
      .filter { it.parent is DialogPanel }
      .filter { it.getGapBefore() == null }
    val width = labels.maxOf { it.preferredSize.width }
    labels.forEach { it.setMinimumWidth(width) }
    */
  }

  private fun JComponent.getConstraints(): CC? {
    val layout = parent.layout as? MigLayout ?: return null
    return layout.getComponentConstraints()[this]
  }

  private fun JComponent.getGapBefore(): BoundSize? {
    return getConstraints()?.horizontal?.gapBefore
  }

  private fun JComponent.setMinimumWidth(width: Int) {
    minimumSize = minimumSize.apply { this.width = width }
  }

  fun setupProject(project: Project) {
    step.setupProject(project)
  }
}
