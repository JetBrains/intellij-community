// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus

/**
 * Deprecated
 *
 * The language new project wizard step was replaced by new project wizard generators.
 * The language selector was moved from the right side of the wizard to the left tray.
 *
 * Please, use [com.intellij.ide.wizard.comment.LinkNewProjectWizardStep] instead of it for navigation between languages.
 *
 * @see GeneratorNewProjectWizard
 * @see com.intellij.ide.wizard.comment.LinkNewProjectWizardStep
 * @see com.intellij.ide.wizard.language.BaseLanguageGeneratorNewProjectWizard.getLanguageModelBuilderId
 * @see NewProjectWizardLanguageStep
 */
@Suppress("DeprecatedCallableAddReplaceWith", "DEPRECATION")
@Deprecated("See LanguageNewProjectWizardData documentation for details")
interface LanguageNewProjectWizardData : NewProjectWizardBaseData {

  @Deprecated("See LanguageNewProjectWizardData documentation for details")
  val languageProperty: GraphProperty<String>

  @Deprecated("See LanguageNewProjectWizardData documentation for details")
  var language: String

  companion object {
    @ApiStatus.Internal
    @Deprecated("See LanguageNewProjectWizardData documentation for details")
    val KEY: Key<LanguageNewProjectWizardData> = Key.create(LanguageNewProjectWizardData::class.java.name)

    @get:ApiStatus.Internal
    @JvmStatic
    @Deprecated("See LanguageNewProjectWizardData documentation for details")
    val NewProjectWizardStep.languageData: LanguageNewProjectWizardData?
      get() = data.getUserData(KEY)
  }
}