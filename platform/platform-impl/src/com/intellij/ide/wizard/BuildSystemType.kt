// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel

interface BuildSystemType<S> : WizardSettingsFactory<S> {
  val name: String

  fun advancedSettings(settings: S): DialogPanel
  fun setupProject(project: Project, settings: S, context: WizardContext)
}

open class BuildSystemWithSettings<S>(
  buildSystemType: BuildSystemType<S>
) : BuildSystemType<S> by buildSystemType, WizardSettingsProvider<S> {

  override val settings by lazy(::createSettings)
  private val advancedSettings by lazy { advancedSettings(settings) }

  fun advancedSettings() = advancedSettings

  fun setupProject(project: Project, context: WizardContext) {
    advancedSettings().apply()
    settingsKey.set(context, settings)
    setupProject(project, settings, context)
  }
}