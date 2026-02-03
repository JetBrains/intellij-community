// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.wizard

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.NlsContexts
import com.intellij.util.PlatformUtils
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import javax.swing.Icon

/**
 * A generator-based project wizard that can be registered as a [com.intellij.ide.util.projectWizard.ModuleBuilder] via
 * [GeneratorNewProjectWizardBuilderAdapter].
 *
 * It's possible to use it to create adapters for [com.intellij.platform.DirectoryProjectGenerator] for minor IDEs as well. See also
 * project generators in PyCharm.
 *
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/new-project-wizard.html#framework-project-generators">
 *   New Project Wizard API: Framework Project Generators (IntelliJ Platform Docs)</a>
 */
interface GeneratorNewProjectWizard {

  companion object {
    @JvmField
    val EP_NAME = ExtensionPointName<GeneratorNewProjectWizard>("com.intellij.newProjectWizard.generator")
  }

  /**
   * The unique identifier to distinguish this generator from others in the new project wizard.
   */
  val id: @NonNls String

  /**
   * The user-visible name of the generator.
   */
  val name: @Nls(capitalization = Nls.Capitalization.Title) String

  /**
   * The icon of the generator.
   */
  val icon: Icon

  /**
   * The ordinal number by which all generators are sorted in the generator tray (on the left).
   */
  val ordinal: Int
    get() = Int.MAX_VALUE

  /**
   * The description of the generator.
   */
  val description: @NlsContexts.DetailedDescription String?
    get() = null

  /**
   * The name that may be used for splitting several generators into groups.
   */
  val groupName: @Nls String?
    get() = null

  /**
   * Disabled generators will be excluded from the new project wizard.
   * For example, you can use [PlatformUtils.isIdeaCommunity] or [PlatformUtils.isIdeaUltimate], etc.
   */
  fun isEnabled(): Boolean = true

  /**
   * Create the main new project wizard step for this generator.
   */
  fun createStep(context: WizardContext): NewProjectWizardStep
}