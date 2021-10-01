// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import javax.swing.DefaultComboBoxModel


abstract class AbstractNewProjectWizardMultiStep<S : NewProjectWizardStep>(
  parent: NewProjectWizardStep,
  epName: ExtensionPointName<out NewProjectWizardMultiStepFactory<S>>
) : AbstractNewProjectWizardStep(parent) {

  protected abstract val self: S

  protected abstract val label: @NlsContexts.Label String
  protected open val comment: @NlsContexts.Label String? = null
  protected open val commentLink: @NlsContexts.Label String? = null

  val stepProperty = propertyGraph.graphProperty { "" }
  var step by stepProperty

  private val steps by lazy {
    epName.extensionList
      .filter { it.isEnabled(context) }
      .associateTo(LinkedHashMap()) { it.name to it.createStep(self) }
  }

  open fun setupCommonUI(builder: Panel) {}

  final override fun setupUI(builder: Panel) {
    with(builder) {
      comment?.let {
        row("") {
          commentHtml(it) {
            commentLink?.let { context.requestSwitchTo(it) }
          }
        }.bottomGap(BottomGap.SMALL)
      }

      row(label) {
        if (steps.size > 4) {
          comboBox(DefaultComboBoxModel(steps.map { it.key }.toTypedArray()))
            .bindItem(stepProperty)
        }
        else {
          segmentedButton(steps.map { it.key }, stepProperty) { it }
        }
      }.bottomGap(BottomGap.SMALL)

      setupCommonUI(this)

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

  final override fun setupProject(project: Project) {
    steps[step]?.setupProject(project)
  }
}