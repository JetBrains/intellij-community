// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.wizard

import com.intellij.icons.AllIcons
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.ide.projectWizard.NewProjectWizardCollector
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys.CONTEXT_COMPONENT
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.IdeaActionButtonLook
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.InstallPluginTask
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.UIBundle
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.components.SegmentedButtonBorder
import java.util.function.Consumer
import java.util.function.Supplier
import javax.swing.JComponent

abstract class AbstractNewProjectWizardMultiStepWithAddButton<S : NewProjectWizardStep, F : NewProjectWizardMultiStepFactory<S>>(
  parent: NewProjectWizardStep,
  epName: ExtensionPointName<F>
) : AbstractNewProjectWizardMultiStep<S, F>(parent, epName) {

  abstract var additionalStepPlugins: Map<String, String>

  override fun setupSwitcherUi(builder: Panel) {
    with(builder) {
      row(label) {
        createAndSetupSwitcher(this@row)

        if (additionalStepPlugins.isNotEmpty()) {
          val plus = AdditionalStepsAction()
          val actionButton = ActionButton(
            plus,
            null,
            ActionPlaces.getPopupPlace("NEW_PROJECT_WIZARD"),
            ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE
          )
          actionButton.setLook(IdeaActionButtonLook())
          actionButton.border = SegmentedButtonBorder()
          cell(actionButton)
        }
      }.bottomGap(BottomGap.SMALL)
    }
  }

  private inner class AdditionalStepsAction : DumbAwareAction(null, null, AllIcons.General.Add) {
    override fun actionPerformed(e: AnActionEvent) {
      NewProjectWizardCollector.logAddPlugin(context)
      val additionalSteps = (additionalStepPlugins.keys - steps.keys).sorted().map { OpenMarketPlaceAction(it) }
      JBPopupFactory.getInstance().createActionGroupPopup(
        UIBundle.message("new.project.wizard.popup.title.install.plugin"), DefaultActionGroup(additionalSteps),
        e.dataContext,
        JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false
      ).show(RelativePoint.getSouthOf(e.getData(CONTEXT_COMPONENT) as JComponent))
    }
  }

  private inner class OpenMarketPlaceAction(private val step: String) : DumbAwareAction(Supplier { step }) {
    override fun actionPerformed(e: AnActionEvent) {
      NewProjectWizardCollector.logPluginSelected(context, step)
      val pluginId = PluginId.getId(additionalStepPlugins[step]!!)
      val component = e.dataContext.getData(CONTEXT_COMPONENT)!!
      if (Registry.`is`("new.project.wizard.modal.plugin.install", false)) {
        ProgressManager.getInstance().run(InstallPluginTask(setOf(pluginId), ModalityState.stateForComponent(component)))
      }
      else {
        ShowSettingsUtil.getInstance().editConfigurable(null, PluginManagerConfigurable(), Consumer {
          it.openMarketplaceTab("/tag: \"Programming Language\" $step")
        })
      }
    }
  }
}