// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus

@ApiStatus.ScheduledForRemoval
@Deprecated("Use instead BuildSystemNewProjectWizardData")
interface NewProjectWizardBuildSystemData : NewProjectWizardLanguageData {

  val buildSystemProperty: GraphProperty<String>

  val buildSystem: String

  companion object {
    val KEY = Key.create<NewProjectWizardBuildSystemData>(NewProjectWizardBuildSystemData::class.java.name)

    private val NewProjectWizardStep.buildSystemData get() = data.getUserData(KEY)!!

    val NewProjectWizardStep.buildSystemProperty get() = buildSystemData.buildSystemProperty
    val NewProjectWizardStep.buildSystem get() = buildSystemData.buildSystem
  }
}