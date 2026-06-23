// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.wizard

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.util.NlsContexts
import com.intellij.util.PlatformUtils

/**
 * Describes fork in steps' tree in the new project wizard.
 * Appends control and logic to switch UI from different vertical wizard steps
 * and applies data from steps which are selected when the wizard's finish button is pressed.
 * Steps can form a tree structure, i.e., direct or indirect child of multistep can be multistep.
 *
 * @see NewProjectWizardStep
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/new-project-wizard.html#steps-forking-the-wizard-flow">
 *   New Project Wizard API: Steps Forking the Wizard Flow (IntelliJ Platform Docs)</a>
 */
@JvmDefaultWithCompatibility
interface NewProjectWizardMultiStepFactory<P : NewProjectWizardStep> {

  /**
   * Name of the created step and label that should be used in multistep switcher.
   */
  val name: @NlsContexts.Label String

  /**
   * The ordinal the steps are sorted by
   */
  val ordinal: Int
    get() = Int.MAX_VALUE

  /**
   * Disabled steps will be excluded from the multistep switcher.
   * For example, you can use [WizardContext.isCreatingNewProject] to filter factory if that factory cannot create new module.
   * Or [PlatformUtils.isIdeaCommunity] or [PlatformUtils.isIdeaUltimate], etc.
   *
   * @param context the context of the wizard where the created step will be displayed.
   */
  fun isEnabled(context: WizardContext): Boolean = true

  /**
   * Creates the new project wizard step with the [parent] step.
   *
   * @param parent is needed to transfer data from parents into child steps.
   */
  fun createStep(parent: P): NewProjectWizardStep
}