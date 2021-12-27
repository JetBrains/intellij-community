// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.wizard

import com.intellij.icons.AllIcons
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.IdeaActionButtonLook
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.UIBundle
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.components.SegmentedButtonBorder
import java.util.function.Consumer
import java.util.function.Supplier
import javax.swing.JComponent

abstract class AbstractNewProjectWizardMultiStepWithAddButton<S : NewProjectWizardStep, F: NewProjectWizardMultiStepFactory<S>>(
  parent: NewProjectWizardStep,
  epName: ExtensionPointName<F>
) : AbstractNewProjectWizardMultiStep<S, F>(parent, epName) {

  abstract var additionalStepPlugins: Map<String, String>

  override fun setupSwitcherUi(builder: Row) {
    super.setupSwitcherUi(builder)
    with(builder) {
      if (additionalStepPlugins.isNotEmpty()) {
        val plus = AdditionalStepsAction()
        cell(ActionButton(plus, plus.templatePresentation, ActionPlaces.getPopupPlace("NEW_PROJECT_WIZARD"),
                          ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE).apply {
          setLook(IdeaActionButtonLook())
          border = SegmentedButtonBorder()
        })
      }
    }
  }

  private inner class AdditionalStepsAction : AnAction(null, null, AllIcons.General.Add) {
    override fun actionPerformed(e: AnActionEvent) {
      val additionalSteps = (additionalStepPlugins.keys - steps.map { it.key }.toSet()).map { OpenMarketPlaceAction(it) }
      JBPopupFactory.getInstance().createActionGroupPopup(
        UIBundle.message("new.project.wizard.popup.title.install.plugin"), DefaultActionGroup(additionalSteps),
        e.dataContext,
        JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false
      ).show(RelativePoint.getSouthOf(e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT) as JComponent))
    }
  }

  private inner class OpenMarketPlaceAction(private val language: String) : AnAction(Supplier { language }) {
    override fun actionPerformed(e: AnActionEvent) {
      ShowSettingsUtil.getInstance().editConfigurable(
        ProjectManager.getInstance().defaultProject,
        PluginManagerConfigurable(),
        Consumer {
          it.openMarketplaceTab(language)
        })
    }
  }
}