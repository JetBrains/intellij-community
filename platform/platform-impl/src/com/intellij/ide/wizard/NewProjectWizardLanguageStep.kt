// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.icons.AllIcons
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.ide.projectWizard.NewProjectWizardCollector.Base.logLanguageAddAction
import com.intellij.ide.projectWizard.NewProjectWizardCollector.Base.logLanguageChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.Base.logLanguageFinished
import com.intellij.ide.projectWizard.NewProjectWizardCollector.Base.logLanguageLoadAction
import com.intellij.ide.projectWizard.NewProjectWizardConstants.Language
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.baseData
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.IdeaActionButtonLook
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.InstallPluginTask
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.UIBundle
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.SegmentedButton
import com.intellij.ui.dsl.builder.components.SegmentedButtonBorder
import com.intellij.util.PlatformUtils
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

class NewProjectWizardLanguageStep(
  parent: NewProjectWizardStep
) : AbstractNewProjectWizardMultiStep<NewProjectWizardLanguageStep, LanguageNewProjectWizard>(parent, LanguageNewProjectWizard.EP_NAME),
    LanguageNewProjectWizardData,
    NewProjectWizardBaseData by parent.baseData!! {

  override val self: NewProjectWizardLanguageStep = this

  override val label: @Nls String = UIBundle.message("label.project.wizard.new.project.language")

  override val languageProperty: GraphProperty<String> by ::stepProperty
  override var language: String by ::step

  private var additionalStepPlugins =
    if (PlatformUtils.isIdeaCommunity())
      mapOf(
        Language.PYTHON to "PythonCore",
        Language.SCALA to "org.intellij.scala"
      )
    else
      mapOf(
        Language.GO to "org.jetbrains.plugins.go",
        Language.RUBY to "org.jetbrains.plugins.ruby",
        Language.PHP to "com.jetbrains.php",
        Language.PYTHON to "Pythonid",
        Language.SCALA to "org.intellij.scala",
        Language.RUST to "com.jetbrains.rust"
      )

  override fun createAndSetupSwitcher(builder: Row): SegmentedButton<String> {
    return super.createAndSetupSwitcher(builder)
      .whenItemSelectedFromUi { logLanguageChanged() }
  }

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

  override fun setupProject(project: Project) {
    super.setupProject(project)

    logLanguageFinished()
  }

  init {
    data.putUserData(LanguageNewProjectWizardData.KEY, this)
  }

  private inner class AdditionalStepsAction : DumbAwareAction(null, null, AllIcons.General.Add) {
    override fun actionPerformed(e: AnActionEvent) {
      logLanguageAddAction()
      val additionalSteps = (additionalStepPlugins.keys - steps.keys).sorted().map { OpenMarketPlaceAction(it) }
      JBPopupFactory.getInstance().createActionGroupPopup(
        UIBundle.message("new.project.wizard.popup.title.install.plugin"), DefaultActionGroup(additionalSteps),
        e.dataContext,
        JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false
      ).show(RelativePoint.getSouthOf(e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT) as JComponent))
    }
  }

  private inner class OpenMarketPlaceAction(private val step: String) : DumbAwareAction({ step }) {
    override fun actionPerformed(e: AnActionEvent) {
      logLanguageLoadAction(step)
      val pluginId = PluginId.getId(additionalStepPlugins[step]!!)
      val component = e.dataContext.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)!!
      if (Registry.`is`("new.project.wizard.modal.plugin.install", false)) {
        ProgressManager.getInstance().run(InstallPluginTask(setOf(pluginId), ModalityState.stateForComponent(component)))
      }
      else {
        ShowSettingsUtil.getInstance().editConfigurable(context.project, PluginManagerConfigurable()) { it ->
          it.openMarketplaceTab("/tag: \"Programming Language\" $step")
        }
      }
    }
  }
}
