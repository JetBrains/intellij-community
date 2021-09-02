// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.openapi.util.NlsContexts

/**
 * Describes fork in steps' tree in new project wizard.
 * Appends control and logic to switch UI from different vertical wizard steps,
 * and applies data from steps which are selected when wizard's finish button is pressed.
 * Steps can form tree structure, i.e. direct or indirect child of multistep can be multistep.
 *
 * @see NewProjectWizardStep
 */
interface NewProjectWizardMultiStepFactory<P : NewProjectWizardStep> : NewProjectWizardStep.Factory<P> {
  /**
   * Name of step and label that should be used in multistep switcher.
   */
  val name: @NlsContexts.Label String

  /**
   * Disabled steps will be excluded from multistep switcher.
   */
  val isEnabled: Boolean get() = true
}