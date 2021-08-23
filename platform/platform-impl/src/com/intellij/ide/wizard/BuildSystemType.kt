// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel

interface BuildSystemType<S> : WizardSettingsFactory<S> {
  val name: String

  fun advancedSettings(settings: S, context: WizardContext): DialogPanel
  fun setupProject(project: Project, settings: S, context: WizardContext)
}

open class BuildSystemWithSettings<S>(
  buildSystemType: BuildSystemType<S>
) : BuildSystemType<S> by buildSystemType, WizardSettingsProvider<S> {

  override val settings by lazy(::createSettings)

  fun advancedSettings(context: WizardContext): DialogPanel {
    settingsKey.set(context, settings)
    return advancedSettings(settings, context)
  }

  fun setupProject(project: Project, context: WizardContext) {
    settingsKey.set(context, settings)
    setupProject(project, settings, context)
  }
}