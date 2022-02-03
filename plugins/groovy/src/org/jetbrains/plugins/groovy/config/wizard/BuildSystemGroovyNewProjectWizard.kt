// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.config.wizard

import com.intellij.ide.wizard.NewProjectWizardMultiStepFactory
import com.intellij.openapi.extensions.ExtensionPointName

interface BuildSystemGroovyNewProjectWizard : NewProjectWizardMultiStepFactory<GroovyNewProjectWizard.Step> {
  companion object {
    val EP_NAME = ExtensionPointName<BuildSystemGroovyNewProjectWizard>("com.intellij.newProjectWizard.groovy.buildSystem")
  }
}