// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.util.NlsContexts

interface NewProjectWizardMultiStepFactory {
  val name: @NlsContexts.Label String

  val isEnabled: Boolean get() = true

  fun createStep(context: WizardContext): NewProjectWizardStep<*>
}