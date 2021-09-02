// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.openapi.observable.properties.GraphProperty
import java.nio.file.Path

interface NewProjectWizardData {

  val nameProperty: GraphProperty<String>
  val pathProperty: GraphProperty<String>
  val gitProperty: GraphProperty<Boolean>

  var name: String
  var path: String
  var git: Boolean

  val projectPath: Path
}