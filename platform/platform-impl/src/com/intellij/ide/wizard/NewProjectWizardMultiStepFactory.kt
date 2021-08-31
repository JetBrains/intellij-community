// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.util.NlsContexts

/**
 * Describes named factory of children steps for multistep.
 *
 * @see NewProjectWizardMultiStep
 */
interface NewProjectWizardMultiStepFactory {
  /**
   * Name of step and label that should be used in multistep switcher.
   */
  val name: @NlsContexts.Label String

  /**
   * Disabled steps will be excluded from multistep switcher.
   */
  val isEnabled: Boolean get() = true

  /**
   * Creates child step in new project wizard [context].
   */
  fun createStep(context: WizardContext): NewProjectWizardStep
}