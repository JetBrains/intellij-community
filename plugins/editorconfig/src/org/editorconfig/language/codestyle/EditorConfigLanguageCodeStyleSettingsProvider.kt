// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codestyle

import com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider
import org.editorconfig.language.EditorConfigLanguage

class EditorConfigLanguageCodeStyleSettingsProvider : LanguageCodeStyleSettingsProvider() {
  override fun getLanguage() = EditorConfigLanguage

  override fun getDefaultCommonSettings(): CommonCodeStyleSettings {
    val settings = CommonCodeStyleSettings(EditorConfigLanguage)
    settings.SPACE_AROUND_ASSIGNMENT_OPERATORS = true
    settings.SPACE_BEFORE_COMMA = false
    settings.SPACE_AFTER_COMMA = true
    settings.SPACE_BEFORE_COLON = false
    settings.SPACE_AFTER_COLON = false
    settings.SPACE_AROUND_EQUALITY_OPERATORS = false
    settings.ALIGN_GROUP_FIELD_DECLARATIONS = false

    return settings
  }

  override fun customizeSettings(consumer: CodeStyleSettingsCustomizable, settingsType: SettingsType) = when (settingsType) {
    SettingsType.SPACING_SETTINGS -> {
      consumer.showStandardOptions(
        "SPACE_AROUND_ASSIGNMENT_OPERATORS",
        "SPACE_BEFORE_COMMA",
        "SPACE_AFTER_COMMA",
        "SPACE_BEFORE_COLON",
        "SPACE_AFTER_COLON"
      )

      consumer.renameStandardOption("SPACE_AROUND_ASSIGNMENT_OPERATORS", "Around separator")
      consumer.moveStandardOption("SPACE_BEFORE_COLON", "Around Operators")
      consumer.moveStandardOption("SPACE_AFTER_COLON", "Around Operators")
    }

    SettingsType.WRAPPING_AND_BRACES_SETTINGS -> {
      consumer.showStandardOptions("ALIGN_GROUP_FIELD_DECLARATIONS")
    }

    else -> Unit
  }

  override fun getCodeSample(settingsType: SettingsType): String =
    """root = true
      |
      |[foo]
      |charset = utf-8
      |key = value1, value2, value3
      |key2 = value4:value5
      |; another comment
      |""".trimMargin()
}
