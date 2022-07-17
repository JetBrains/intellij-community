// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.util.Key

@Deprecated("Use instead LanguageNewProjectWizardData")
interface NewProjectWizardLanguageData : NewProjectWizardBaseData {

  val languageProperty: GraphProperty<String>

  val language: String

  companion object {
    val KEY = Key.create<NewProjectWizardLanguageData>(NewProjectWizardLanguageData::class.java.name)

    val NewProjectWizardStep.languageData get() = data.getUserData(KEY)!!

    val NewProjectWizardStep.languageProperty get() = languageData.languageProperty
    val NewProjectWizardStep.language get() = languageData.language
  }
}