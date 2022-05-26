// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.validateAfterPropagation
import com.intellij.util.ui.JBUI

class NewProjectWizardStepPanel(val step: NewProjectWizardStep) {
  fun getPreferredFocusedComponent() = component.preferredFocusedComponent

  fun validate() =
    component.validateCallbacks
      .mapNotNull { it() }
      .map { it.also(::logValidationInfoInHeadlessMode) }
      .all { it.okEnabled }

  private fun logValidationInfoInHeadlessMode(info: ValidationInfo) {
    if (ApplicationManager.getApplication().isHeadlessEnvironment) {
      logger<ModuleBuilder>().warn(info.message)
    }
  }

  fun isModified() = component.isModified()

  fun apply() = component.apply()

  fun reset() = component.reset()

  val component by lazy {
    panel {
      validateAfterPropagation(step.propertyGraph)
      step.setupUI(this)
    }.apply {
      registerValidators(step.context.disposable)
      withBorder(JBUI.Borders.empty(14, 20))
      setMinimumWidthForAllRowLabels(JBUI.scale(90))
    }
  }
}