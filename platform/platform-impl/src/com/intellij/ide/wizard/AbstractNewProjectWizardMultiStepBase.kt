// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.openapi.observable.properties.AtomicObservableProperty
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.observable.util.bindStorage
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.gridLayout.HorizontalAlign


abstract class AbstractNewProjectWizardMultiStepBase(
  parent: NewProjectWizardStep
) : AbstractNewProjectWizardStep(parent) {

  protected abstract val label: @NlsContexts.Label String

  internal val stepsProperty = AtomicObservableProperty<Map<String, NewProjectWizardStep>>(emptyMap())
  var steps: Map<String, NewProjectWizardStep> by stepsProperty

  val stepProperty = propertyGraph.graphProperty { "" }.bindStorage("${javaClass.name}.selectedStep")
  var step by stepProperty

  protected open fun initSteps() = emptyMap<String, NewProjectWizardStep>()

  open fun setupSwitcherUi(builder: Row) {
    with(builder) {
      val segmentedButton = segmentedButton(steps.keys) { it }
        .bind(stepProperty)
      stepsProperty.afterChange {
        segmentedButton.items(steps.keys)
      }
    }
  }

  override fun setupUI(builder: Panel) {
    steps = initSteps()
    if (!steps.keys.contains(step)) step = ""
    step = step.ifBlank { steps.keys.first() }

    keywords.add(steps.keys)

    with(builder) {
      row(label) {
        setupSwitcherUi(this@row)
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
        updateStepPanels(stepsPanels, panelBuilder)
      }
      updateStepPanels(stepsPanels, panelBuilder)
    }
  }

  private fun updateStepPanels(stepsPanels: HashMap<String, DialogPanel>, panelBuilder: NewProjectWizardPanelBuilder) {
    for ((key, panel) in stepsPanels) {
      panelBuilder.setVisible(panel, key == step)
      panel.repaint()
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