// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.util.Key

interface LanguageNewProjectWizardData : NewProjectWizardBaseData {

  val languageProperty: GraphProperty<String>

  var language: String

  companion object {

    val KEY: Key<LanguageNewProjectWizardData> = Key.create(LanguageNewProjectWizardData::class.java.name)

    @JvmStatic
    val NewProjectWizardStep.languageData: LanguageNewProjectWizardData?
      get() = data.getUserData(KEY)
  }
}