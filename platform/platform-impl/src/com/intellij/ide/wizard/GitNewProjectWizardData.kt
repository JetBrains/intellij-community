// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.util.Key

interface GitNewProjectWizardData {

  val gitProperty: GraphProperty<Boolean>

  var git: Boolean

  companion object {
    val KEY = Key.create<GitNewProjectWizardData>(GitNewProjectWizardData::class.java.name)

    val NewProjectWizardStep.gitData get() = data.getUserData(KEY)!!

    val NewProjectWizardStep.gitProperty get() = gitData.gitProperty
    val NewProjectWizardStep.git get() = gitData.git
  }
}