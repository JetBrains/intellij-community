// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import javax.swing.DefaultComboBoxModel


abstract class AbstractNewProjectWizardMultiStepBase(
  parent: NewProjectWizardStep
) : AbstractNewProjectWizardStep(parent) {

  protected abstract val label: @NlsContexts.Label String

  protected abstract val steps: Map<String, NewProjectWizardStep>

  val stepProperty = propertyGraph.graphProperty { "" }
  var step by stepProperty

  override fun setupUI(builder: Panel) {
    with(builder) {
      row(label) {
        if (steps.size > 4) {
          comboBox(DefaultComboBoxModel(steps.map { it.key }.toTypedArray()))
            .bindItem(stepProperty)
        }
        else {
          segmentedButton(steps.map { it.key }, stepProperty) { it }
        }
      }.bottomGap(BottomGap.SMALL)

      val panelBuilder = NewProjectWizardPanelBuilder.getInstance(context)
      val stepsPanels = HashMap<String, DialogPanel>()
      for ((name, step) in steps) {
        val panel = panelBuilder.panel(step::setupUI)
        row {
          cell(panel)
            .horizontalAlign(HorizontalAlign.FILL)
        }
        stepsPanels[name] = panel
      }
      stepProperty.afterChange {
        for ((key, panel) in stepsPanels) {
          panel.isVisible = key == step
        }
      }
      step = steps.keys.first()
    }
  }

  override fun setupProject(project: Project) {
    steps[step]?.setupProject(project)
  }

  fun whenStepSelected(name: String, action: () -> Unit) {
    if (step == name) {
      action()
    }
    else {
      val disposable = Disposer.newDisposable(context.disposable, "")
      stepProperty.afterChange({
        if (it == name) {
          Disposer.dispose(disposable)
          action()
        }
      }, disposable)
    }
  }
}