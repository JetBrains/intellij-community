// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.missingApi

import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.InvalidDataException
import com.intellij.ui.components.fields.valueEditors.TextFieldValueEditor
import org.jetbrains.idea.devkit.DevKitBundle
import javax.swing.JTextField

/**
 * [TextFieldValueEditor] for [BuildNumber]s that can be used to validate user-inputed build numbers.
 */
class BuildNumberValueEditor(buildField: JTextField, valueName: String?, defaultValue: BuildNumber)
  : TextFieldValueEditor<BuildNumber>(buildField, valueName, defaultValue) {

  override fun parseValue(text: String?): BuildNumber {
    if (text.isNullOrBlank()) {
      return defaultValue
    }
    return BuildNumber.fromStringOrNull(text)
           ?: throw InvalidDataException(DevKitBundle.message("inspections.missing.recent.api.settings.invalid.build.number", text))
  }

  override fun valueToString(value: BuildNumber) = value.asString()

  override fun isValid(value: BuildNumber) = true
}
