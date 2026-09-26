// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import javax.swing.JComponent

class NewProjectWizardStepPanel(val step: NewProjectWizardStep) {

  fun getPreferredFocusedComponent(): JComponent? = component.preferredFocusedComponent

  fun validate(): Boolean = component.validateAll().all { it.okEnabled }

  fun isModified(): Boolean = component.isModified()

  fun apply(): Unit = component.apply()

  fun reset(): Unit = component.reset()

  val component: DialogPanel by lazy {
    panel {
      step.setupUI(this)
    }.withVisualPadding(topField = true)
      .apply {
        registerValidators(step.context.disposable)
        setMinimumWidthForAllRowLabels(JBUI.scale(90))
      }
  }
}