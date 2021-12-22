// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.icons.AllIcons
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.IdeaActionButtonLook
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.UIBundle
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.components.SegmentedButtonBorder
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.layout.*
import com.intellij.util.ResettableLazy
import com.intellij.util.ui.accessibility.ScreenReader
import javax.swing.JComponent


abstract class AbstractNewProjectWizardMultiStepBase(
  parent: NewProjectWizardStep
) : AbstractNewProjectWizardStep(parent) {

  protected abstract val label: @NlsContexts.Label String

  protected abstract val steps: ResettableLazy<Map<String, NewProjectWizardStep>>

  protected open val additionalSteps: ResettableLazy<List<AnAction>>? = null

  val stepProperty = propertyGraph.graphProperty { "" }
    .apply { bindWithStorage("NEW_PROJECT_WIZARD_STEP-${this@AbstractNewProjectWizardMultiStepBase.javaClass.name}") }
  var step by stepProperty

  private lateinit var stepsPanel: Placeholder

  override fun setupUI(builder: Panel) {
    with(builder) {
      row(label) {
        ApplicationManager.getApplication().messageBus.connect().subscribe(
          DynamicPluginListener.TOPIC, object : DynamicPluginListener {
          override fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
            steps.reset()
            additionalSteps?.reset()
            applySteps()
          }
        })
        stepsPanel = placeholder()
        applySteps()
        if (additionalSteps != null) {
          val plus = AdditionalStepsAction()
          cell(ActionButton(plus, plus.templatePresentation, ActionPlaces.getPopupPlace("NEW_PROJECT_WIZARD"),
                            ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE).apply {
            setLook(IdeaActionButtonLook())
            border = SegmentedButtonBorder()
          })
        }
      }.bottomGap(BottomGap.SMALL)

      val panelBuilder = NewProjectWizardPanelBuilder.getInstance(context)
      val stepsPanels = HashMap<String, DialogPanel>()
      for ((name, step) in steps.value) {
        val panel = panelBuilder.panel(step::setupUI)
        row {
          cell(panel)
            .horizontalAlign(HorizontalAlign.FILL)
        }
        stepsPanels[name] = panel
      }
      stepProperty.afterChange {
        for ((key, panel) in stepsPanels) {
          panelBuilder.setVisible(panel, key == step)
        }
      }
      step = stepProperty.get().ifBlank { steps.value.keys.first() }
    }
  }

  private fun Row.applySteps() {
    val actualSteps = steps.value
    if (actualSteps.size > 6 || ScreenReader.isActive()) {
      stepsPanel.component = comboBox(actualSteps.map { it.key }).bindItem(stepProperty).component
    }
    else {
      stepsPanel.component = segmentedButton(actualSteps.map { it.key }, stepProperty) { it }.component
    }
  }

  override fun setupProject(project: Project) {
    steps.value[step]?.setupProject(project)
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

  private inner class AdditionalStepsAction : AnAction(null, null, AllIcons.General.Add) {
    override fun actionPerformed(e: AnActionEvent) {
      JBPopupFactory.getInstance().createActionGroupPopup(
        UIBundle.message("new.project.wizard.popup.title.install.plugin"), DefaultActionGroup(additionalSteps!!.value),
        e.dataContext,
        JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false
      ).show(RelativePoint.getSouthOf(e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT) as JComponent))
    }
  }
}