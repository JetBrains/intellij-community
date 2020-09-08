// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.missingApi

import com.intellij.openapi.util.BuildNumber
import com.intellij.ui.components.JBTextField
import org.jetbrains.annotations.NonNls

/**
 * Text field used to input build numbers.
 */
class BuildNumberField(@NonNls valueName: String, defaultValue: BuildNumber) : JBTextField() {
  val valueEditor: BuildNumberValueEditor = BuildNumberValueEditor(this, valueName, defaultValue)

  var value: BuildNumber
    get() = valueEditor.value
    set(value) {
      valueEditor.value = value
    }
}