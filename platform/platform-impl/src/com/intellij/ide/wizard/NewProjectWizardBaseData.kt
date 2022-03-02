// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.util.Key
import java.nio.file.Path

interface NewProjectWizardBaseData {

  val nameProperty: GraphProperty<String>
  val pathProperty: GraphProperty<String>

  var name: String
  var path: String // canonical

  /**
   * @deprecated projectPath throws exception when it isn't validated
   */
  @Deprecated("Unsafe", ReplaceWith("Path.of(path, name)", "java.nio.file.Path"))
  @JvmDefault
  val projectPath: Path
    get() = Path.of(path, name)

  companion object {
    @JvmStatic val KEY = Key.create<NewProjectWizardBaseData>(NewProjectWizardBaseData::class.java.name)

    @JvmStatic val NewProjectWizardStep.baseData get() = data.getUserData(KEY)!!

    @JvmStatic val NewProjectWizardStep.nameProperty get() = baseData.nameProperty
    @JvmStatic val NewProjectWizardStep.pathProperty get() = baseData.pathProperty
    @JvmStatic var NewProjectWizardStep.name get() = baseData.name; set(it) { baseData.name = it }
    @JvmStatic var NewProjectWizardStep.path get() = baseData.path; set(it) { baseData.path = it }
  }
}