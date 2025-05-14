// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.wizard.language

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.util.PlatformUtils
import org.jetbrains.annotations.Nls
import javax.swing.Icon

/**
 * A generator-based project wizard for a specific language.
 * These generators are present in the first part of the generator tray (on the left).
 *
 * @see com.intellij.ide.wizard.GeneratorNewProjectWizard
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/new-project-wizard.html#language-project-generators">
 *   New Project Wizard API: Language Project Generators (IntelliJ Platform Docs)</a>
 */
interface LanguageGeneratorNewProjectWizard {

  /**
   * The user-visible name of the generator.
   *
   * @see com.intellij.ide.wizard.GeneratorNewProjectWizard.name
   */
  val name: @Nls(capitalization = Nls.Capitalization.Title) String

  /**
   * The icon of the generator.
   *
   * @see com.intellij.ide.wizard.GeneratorNewProjectWizard.icon
   */
  val icon: Icon

  /**
   * The ordinal number by which all languages are sorted in the generator tray (on the left).
   *
   * @see com.intellij.ide.wizard.GeneratorNewProjectWizard.ordinal
   */
  val ordinal: Int
    get() = Int.MAX_VALUE

  /**
   * Disabled generators will be excluded from the new project wizard.
   * For example, you can use [WizardContext.isCreatingNewProject] to filter factory if that factory cannot create new module.
   * Or [PlatformUtils.isIdeaCommunity] or [PlatformUtils.isIdeaUltimate], etc.
   *
   * @param context the context of the wizard where the created step will be displayed.
   *
   * @see com.intellij.ide.wizard.GeneratorNewProjectWizard.isEnabled
   */
  fun isEnabled(context: WizardContext): Boolean = true

  /**
   * Creates the new project wizard step with the [parent] step.
   *
   * @param parent is needed to transfer data from parents into child steps.
   *
   * @see com.intellij.ide.wizard.GeneratorNewProjectWizard.createStep
   */
  fun createStep(parent: NewProjectWizardStep): NewProjectWizardStep

  companion object {

    @JvmField
    val EP_NAME = ExtensionPointName<LanguageGeneratorNewProjectWizard>("com.intellij.newProjectWizard.languageGenerator")
  }
}