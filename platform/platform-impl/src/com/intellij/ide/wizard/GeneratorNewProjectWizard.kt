// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.util.NlsContexts
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import javax.swing.Icon

/**
 * A generator-based project wizard that can be registered as a [com.intellij.ide.util.projectWizard.ModuleBuilder] via
 * [GeneratorNewProjectWizardBuilderAdapter].
 *
 * It's possible to use it to create adapters for [com.intellij.platform.DirectoryProjectGenerator] for minor IDEs as well. See also
 * project generators in PyCharm.
 */
interface GeneratorNewProjectWizard {
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
   * Create the main new project wizard step for this generator.
   */
  fun createStep(context: WizardContext): NewProjectWizardStep
}