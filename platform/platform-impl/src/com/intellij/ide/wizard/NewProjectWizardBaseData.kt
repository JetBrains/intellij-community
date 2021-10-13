// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.util.Key
import java.nio.file.Path

interface NewProjectWizardBaseData {

  val nameProperty: GraphProperty<String>
  val pathProperty: GraphProperty<String>

  var name: String
  var path: String

  val projectPath: Path

  companion object {
    val KEY = Key.create<NewProjectWizardBaseData>(NewProjectWizardBaseData::class.java.name)

    val NewProjectWizardStep.baseData get() = data.getUserData(KEY)!!

    val NewProjectWizardStep.nameProperty get() = baseData.nameProperty
    val NewProjectWizardStep.pathProperty get() = baseData.pathProperty
    val NewProjectWizardStep.name get() = baseData.name
    val NewProjectWizardStep.path get() = baseData.path
    val NewProjectWizardStep.projectPath get() = baseData.projectPath
  }
}