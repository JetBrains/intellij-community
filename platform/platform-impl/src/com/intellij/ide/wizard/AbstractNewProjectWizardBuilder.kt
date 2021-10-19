// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.ModifiableModuleModel
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.gridLayout.GridLayout
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JTextField

abstract class AbstractNewProjectWizardBuilder : ModuleBuilder() {
  private var step: BridgeStep? = null

  abstract override fun getPresentableName(): String
  abstract override fun getDescription(): String
  abstract override fun getNodeIcon(): Icon

  protected abstract fun createStep(context: WizardContext): NewProjectWizardStep

  final override fun getModuleType() =
    object : ModuleType<AbstractNewProjectWizardBuilder>("newWizard.${this::class.java.name}") {
      override fun createModuleBuilder() = this@AbstractNewProjectWizardBuilder
      override fun getName() = this@AbstractNewProjectWizardBuilder.presentableName
      override fun getDescription() = this@AbstractNewProjectWizardBuilder.description
      override fun getNodeIcon(isOpened: Boolean) = this@AbstractNewProjectWizardBuilder.nodeIcon
    }

  final override fun getCustomOptionsStep(context: WizardContext, parentDisposable: Disposable): ModuleWizardStep {
    return BridgeStep(context, createStep(context))
      .also { step = it }
  }

  override fun commitModule(project: Project, model: ModifiableModuleModel?): Nothing? {
    step!!.setupProject(project)
    return null
  }

  override fun cleanup() {
    step = null
  }

  private class BridgeStep(context: WizardContext, private val step: NewProjectWizardStep) : ModuleWizardStep() {

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
      return constraints != null && constraints.x == 0 && constraints.gaps.left == 0
    }

    private fun JComponent.setMinimumWidth(width: Int) {
      minimumSize = minimumSize.apply { this.width = width }
    }
  }
}