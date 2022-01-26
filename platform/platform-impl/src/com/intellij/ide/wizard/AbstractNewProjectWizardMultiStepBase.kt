// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.observable.util.bindStorage
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.util.ui.JBUI


abstract class AbstractNewProjectWizardMultiStepBase(
  parent: NewProjectWizardStep
) : AbstractNewProjectWizardStep(parent) {

  protected abstract val label: @NlsContexts.Label String

  val stepsProperty = AtomicProperty<Map<String, NewProjectWizardStep>>(emptyMap())
  var steps: Map<String, NewProjectWizardStep> by stepsProperty

  val stepProperty = propertyGraph.graphProperty { "" }
    .bindStorage("${javaClass.name}.selectedStep")
  var step by stepProperty

  private val stepsPanels = HashMap<String, DialogPanel>()

  protected open fun initSteps() = emptyMap<String, NewProjectWizardStep>()

  protected open fun setupSwitcherUi(builder: Row) {
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

    with(builder) {
      row(label) {
        setupSwitcherUi(this@row)
      }.bottomGap(BottomGap.SMALL)
      row {
        val placeholder = placeholder()
          .horizontalAlign(HorizontalAlign.FILL)

        placeholder.component = getOrCreateStepPanel()
        stepProperty.afterChange {
          placeholder.component = getOrCreateStepPanel()
        }
      }
    }
  }

  private fun getOrCreateStepPanel(): DialogPanel? {
    if (step !in stepsPanels) {
      val stepUi = steps[step] ?: return null
      val panel = panel {
        validateAfterPropagation(stepUi.propertyGraph)
        stepUi.setupUI(this)
      }
      panel.registerValidators(stepUi.context.disposable)
      panel.setMinimumWidthForAllRowLabels(JBUI.scale(90))
      stepsPanels[step] = panel
    }
    return stepsPanels[step]
  }

  override fun setupProject(project: Project) {
    steps[step]?.setupProject(project)
  }

  init {
    stepsProperty.afterChange {
      keywords.add(this, steps.keys)
    }
    stepsProperty.afterChange {
      stepsPanels.clear()
    }
    var oldSteps: Set<String> = emptySet()
    stepsProperty.afterChange {
      val addedSteps = steps.keys - oldSteps
      step = when {
        oldSteps.isNotEmpty() && addedSteps.isNotEmpty() -> addedSteps.first()
        step.isEmpty() -> steps.keys.first()
        step !in steps -> steps.keys.first()
        else -> step // Update all dependent things
      }
      oldSteps = steps.keys
    }
  }
}