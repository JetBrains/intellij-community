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

/**
 * Describes fork in steps' tree in new project wizard.
 * Appends control and logic to switch UI from different vertical wizard steps,
 * and applies data from steps which are selected when wizard's finish button is pressed.
 * Steps can form tree structure, i.e. direct or indirect child of multistep can be multistep.
 *
 * @param epName is extension point which provides children steps to switch.
 * @property commonSteps are common steps that should be displayed between switcher and children steps.
 *
 * @see NewProjectWizardStep
 */
abstract class NewProjectWizardMultiStep(
  context: WizardContext,
  epName: ExtensionPointName<out NewProjectWizardMultiStepFactory>
) : NewProjectWizardStep(context) {

  protected abstract val label: @NlsContexts.Label String

  protected open val commonSteps = emptyList<NewProjectWizardStep>()

  val stepProperty = propertyGraph.graphProperty { "" }
  var step by stepProperty

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