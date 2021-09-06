// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.layout.*
import javax.swing.DefaultComboBoxModel


abstract class AbstractNewProjectWizardMultiStep<P : NewProjectWizardStep, S : NewProjectWizardStep>(
  parent: P,
  epName: ExtensionPointName<out NewProjectWizardMultiStepFactory<S>>
) : AbstractNewProjectWizardChildStep<P>(parent) {

  protected abstract val self: S

  protected abstract val label: @NlsContexts.Label String

  protected open val commonStep: NewProjectWizardStep? = null

  val stepProperty = propertyGraph.graphProperty { "" }
  var step by stepProperty

  private val steps by lazy {
    epName.extensionList
      .filter { it.isEnabled }
      .associateTo(LinkedHashMap()) { it.name to it.createStep(self) }
  }

  final override fun setupUI(builder: LayoutBuilder) {
    with(builder) {
      row(label) {
        if (steps.size > 4) {
          comboBox(DefaultComboBoxModel(steps.map { it.key }.toTypedArray()), stepProperty)
        }
        else {
          buttonSelector(steps.map { it.key }, stepProperty) { it }
        }
      }.largeGapAfter()

      commonStep?.setupUI(this)

      val panelBuilder = NewProjectWizardPanelBuilder.getInstance(context)
      val stepsPanels = HashMap<String, DialogPanel>()
      for ((name, step) in steps) {
        val panel = panelBuilder.panel(step::setupUI)
        row { panel(growX) }
        stepsPanels[name] = panel
      }
      stepProperty.afterChange {
        stepsPanels.values.forEach { it.isVisible = false }
        stepsPanels[step]?.isVisible = true
      }
      step = steps.keys.first()
    }
  }

  final override fun setupProject(project: Project) {
    commonStep?.setupProject(project)
    steps[step]?.setupProject(project)
  }
}