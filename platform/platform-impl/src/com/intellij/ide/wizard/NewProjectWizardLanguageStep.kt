// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.ide.projectWizard.NewProjectWizardCollector
import com.intellij.ide.projectWizard.NewProjectWizardCollector.Companion.logLanguageFinished
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.baseData
import com.intellij.openapi.project.Project
import com.intellij.ui.UIBundle
import com.intellij.util.PlatformUtils

class NewProjectWizardLanguageStep(parent: NewProjectWizardStep) :
  AbstractNewProjectWizardMultiStepWithAddButton<NewProjectWizardLanguageStep, LanguageNewProjectWizard>(parent, LanguageNewProjectWizard.EP_NAME),
  LanguageNewProjectWizardData,
  NewProjectWizardBaseData by parent.baseData {

  override val self = this

  override val label = UIBundle.message("label.project.wizard.new.project.language")

  override val languageProperty by ::stepProperty
  override var language by ::step

  override var additionalStepPlugins = allLanguages

  init {
    data.putUserData(LanguageNewProjectWizardData.KEY, this)
    languageProperty.afterChange {
      NewProjectWizardCollector.logLanguageChanged(context, step)
    }
  }

  override fun setupProject(project: Project) {
    super.setupProject(project)
    logLanguageFinished(context, step)
  }

  companion object {
    private val ultimate = mapOf(
      "Go" to "org.jetbrains.plugins.go",
      "Ruby" to "org.jetbrains.plugins.ruby",
      "PHP" to "com.jetbrains.php"
    )
    private val community = mapOf("Scala" to "org.intellij.scala")
    private val pythonCommunity = mapOf("Python" to "PythonCore")
    private val pythonUltimate = mapOf("Python" to "Pythonid")

    val allLanguages = if (PlatformUtils.isIdeaCommunity()) pythonCommunity + community else ultimate + pythonUltimate + community
  }
}
