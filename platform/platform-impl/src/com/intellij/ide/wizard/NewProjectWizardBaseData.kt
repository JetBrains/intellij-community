// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.wizard

import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.util.Key
import java.nio.file.Path

interface NewProjectWizardBaseData {

  val nameProperty: GraphProperty<String>

  var name: String

  val pathProperty: GraphProperty<String>

  var path: String // canonical

  val contentEntryPath: String // canonical
    get() = "$path/$name"

  @Deprecated(
    message = "Unsafe: projectPath throws exception when it isn't validated",
    replaceWith = ReplaceWith("Path.of(path, name)", "java.nio.file.Path"),
    level = DeprecationLevel.ERROR
  )
  val projectPath: Path
    get() = Path.of(path).resolve(name)

  companion object {

    val KEY: Key<NewProjectWizardBaseData> = Key.create(NewProjectWizardBaseData::class.java.name)

    @JvmStatic
    val NewProjectWizardStep.baseData: NewProjectWizardBaseData?
      get() = data.getUserData(KEY)
  }
}