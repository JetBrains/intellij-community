// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.layout.*
import javax.swing.DefaultComboBoxModel

abstract class NewProjectWizardMultiStep(
  context: WizardContext,
  epName: ExtensionPointName<out NewProjectWizardMultiStepFactory>
) : NewProjectWizardStep(context) {

  protected abstract val label: @NlsContexts.Label String

  protected open val commonSteps = emptyList<NewProjectWizardStep>()

  protected val stepProperty = propertyGraph.graphProperty { "" }
  protected var step by stepProperty

  private val steps = epName.extensionList
    .filter { it.isEnabled }
    .associateTo(LinkedHashMap()) { it.name to it.createStep(context) }

  final override fun setupUI(builder: RowBuilder) {
    with(builder) {
      row(label) {
        if (steps.size > 4) {
          comboBox(DefaultComboBoxModel(steps.map { it.key }.toTypedArray()), stepProperty)
        }
        else {
          buttonSelector(steps.map { it.key }, stepProperty) { it }
        }
      }.largeGapAfter()

      commonSteps.forEach { it.setupUI(this) }

      val stepsControllers = HashMap<String, DialogPanel>()
      for ((name, step) in steps) {
        stepsControllers[name] = nestedPanel {
          step.setupUI(this)
        }.component
      }
      stepProperty.afterChange {
        stepsControllers.values.forEach { it.isVisible = false }
        stepsControllers[step]?.isVisible = true
      }
      step = steps.keys.first()
    }
  }

  final override fun setupProject(project: Project) {
    commonSteps.forEach { it.setupProject(project) }
    steps[step]?.setupProject(project)
  }
}