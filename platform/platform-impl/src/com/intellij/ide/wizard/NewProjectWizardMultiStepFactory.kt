// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.util.NlsContexts

/**
 * Describes fork in steps' tree in new project wizard.
 * Appends control and logic to switch UI from different vertical wizard steps,
 * and applies data from steps which are selected when wizard's finish button is pressed.
 * Steps can form tree structure, i.e. direct or indirect child of multistep can be multistep.
 *
 * @see NewProjectWizardStep
 */
interface NewProjectWizardMultiStepFactory<P : NewProjectWizardStep> {
  /**
   * Name of step and label that should be used in multistep switcher.
   */
  val name: @NlsContexts.Label String

  /**
   * The ordinal the steps are sorted by
   */
  @JvmDefault
  val ordinal: Int
    get() = Int.MAX_VALUE

  /**
   * Disabled steps will be excluded from multistep switcher.
   *
   * @param context is context of wizard where created step will be displayed
   * Use [WizardContext.isCreatingNewProject] to filter factory if that cannot create new module.
   */
  fun isEnabled(context: WizardContext): Boolean = true

  /**
   * Creates new project wizard step with parent step.
   * Parent is needed to transfer data from parents into children steps.
   */
  fun createStep(parent: P): NewProjectWizardStep
}