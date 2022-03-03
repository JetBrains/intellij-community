// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.config.wizard

import com.intellij.ide.projectWizard.NewProjectWizardCollector
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.NewProjectWizardMultiStepFactory
import com.intellij.openapi.extensions.ExtensionPointName

interface BuildSystemGroovyNewProjectWizard : NewProjectWizardMultiStepFactory<GroovyNewProjectWizard.Step> {
  companion object {
    val EP_NAME = ExtensionPointName<BuildSystemGroovyNewProjectWizard>("com.intellij.newProjectWizard.groovy.buildSystem")

    private val collector = NewProjectWizardCollector.BuildSystemCollector(EP_NAME.extensions.map { it.name })

    @JvmStatic
    fun logBuildSystemChanged(context: WizardContext, name: String) = collector.logBuildSystemChanged(context, name)
  }
}