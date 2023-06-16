// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus

@ApiStatus.ScheduledForRemoval
@Deprecated("Use instead LanguageNewProjectWizardData")
interface NewProjectWizardLanguageData : NewProjectWizardBaseData {

  val languageProperty: GraphProperty<String>

  val language: String

  companion object {
    val KEY: Key<NewProjectWizardLanguageData> = Key.create(NewProjectWizardLanguageData::class.java.name)

    private val NewProjectWizardStep.languageData get() = data.getUserData(KEY)!!

    val NewProjectWizardStep.languageProperty: GraphProperty<String> get() = languageData.languageProperty
    val NewProjectWizardStep.language: String get() = languageData.language
  }
}