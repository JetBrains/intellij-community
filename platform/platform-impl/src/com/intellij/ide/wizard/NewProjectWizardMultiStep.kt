// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.layout.*
import javax.swing.DefaultComboBoxModel

abstract class NewProjectWizardMultiStep<S : NewProjectWizardMultiStep.Settings<S>>(
  private val context: WizardContext,
  epName: ExtensionPointName<out NewProjectWizardMultiStepFactory>
) : NewProjectWizardStep<S> {

  abstract val label: @NlsContexts.Label String

  protected open fun setupChildUI(builder: RowBuilder) {}

  protected open fun setupChildProjectData(project: Project) {}

  private val steps = epName.extensionList
    .filter { it.isEnabled }
    .associateTo(LinkedHashMap()) { it.name to it.createStep(context) }

  final override fun setupUI(builder: RowBuilder) {
    with(builder) {
      row(label) {
        if (steps.size > 4) {
          comboBox(DefaultComboBoxModel(steps.map { it.key }.toTypedArray()), settings.stepProperty)
        }
        else {
          buttonSelector(steps.map { it.key }, settings.stepProperty) { it }
        }
      }

      setupChildUI(this)

      val stepsControllers = HashMap<String, DialogPanel>()
      for ((name, step) in steps) {
        stepsControllers[name] = nestedPanel {
          step.setupUI(this)
        }.component
      }
      settings.stepProperty.afterChange {
        stepsControllers.values.forEach { it.isVisible = false }
        stepsControllers[settings.step]?.isVisible = true
      }
      settings.step = steps.keys.first()
    }
  }

  final override fun setupProject(project: Project) {
    setupChildProjectData(project)
    steps[settings.step]?.setupProject(project)
  }

  abstract class Settings<S : Settings<S>>(key: Key<S>, context: WizardContext) : NewProjectWizardStepSettings<S>(key, context) {
    val stepProperty = propertyGraph.graphProperty { "" }
    var step by stepProperty
  }
}