// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.ide.projectWizard.NewProjectWizardCollector
import com.intellij.ide.projectWizard.NewProjectWizardCollector.Companion.logLanguageFinished
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.baseData
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.UIBundle
import com.intellij.ui.dsl.builder.Row
import java.util.function.Consumer
import java.util.function.Supplier

class NewProjectWizardLanguageStep(parent: NewProjectWizardStep) :
  AbstractNewProjectWizardMultiStepWithAddButton<NewProjectWizardLanguageStep, LanguageNewProjectWizard>(parent, LanguageNewProjectWizard.EP_NAME),
  LanguageNewProjectWizardData,
  NewProjectWizardBaseData by parent.baseData {

  override val self = this

  override val label = UIBundle.message("label.project.wizard.new.project.language")

  override val languageProperty by ::stepProperty
  override var language by ::step

  init {
    data.putUserData(LanguageNewProjectWizardData.KEY, this)
    languageProperty.afterChange {
      NewProjectWizardCollector.logLanguageChanged(context, this::class.java)
    }
  }

  override fun setupSwitcherUi(builder: Row) {
    additionalSteps = initAdditionalSteps()
    super.setupSwitcherUi(builder)
    stepsProperty.afterChange {
      additionalSteps = initAdditionalSteps()
    }
  }

  override fun setupProject(project: Project) {
    super.setupProject(project)
    logLanguageFinished(context, this::class.java)
  }

  private fun initAdditionalSteps() = (languages.map { it } - steps.map { it.key }.toSet()).map { LanguageAction(it) }

  companion object {
    private val languages = listOf("Java", "Kotlin", "JavaScript", "Groovy", "Go", "Ruby", "PHP", "Python", "Scala")

    private class LanguageAction(private val language: String) : AnAction(Supplier { language }) {
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
}
