// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.ui.dsl.builder.AFTER_GRAPH_PROPAGATION
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI

class NewProjectWizardStepPanel(val step: NewProjectWizardStep) {

  fun getPreferredFocusedComponent() = component.preferredFocusedComponent

  fun validate() = component.validateAll().all { it.okEnabled }

  fun isModified() = component.isModified()

  fun apply() = component.apply()

  fun reset() = component.reset()

  val component by lazy {
    panel {
      validationRequestor(AFTER_GRAPH_PROPAGATION(step.propertyGraph))
      step.setupUI(this)
    }.apply {
      registerValidators(step.context.disposable)
      withBorder(JBUI.Borders.empty(14, 20))
      setMinimumWidthForAllRowLabels(JBUI.scale(90))
    }
  }
}