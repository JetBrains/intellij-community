// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codestyle

import com.intellij.psi.codeStyle.*
import com.intellij.psi.codeStyle.CommonCodeStyleSettings.IndentOptions
import org.editorconfig.Utils
import org.editorconfig.language.EditorConfigLanguage
import org.editorconfig.language.messages.EditorConfigBundle

class EditorConfigLanguageCodeStyleSettingsProvider : LanguageCodeStyleSettingsProvider() {
  override fun getLanguage(): EditorConfigLanguage = EditorConfigLanguage

  override fun createConfigurable(baseSettings: CodeStyleSettings, modelSettings: CodeStyleSettings):
    CodeStyleConfigurable = EditorConfigCodeStyleConfigurable(baseSettings, modelSettings)

  override fun getConfigurableDisplayName(): String = Utils.EDITOR_CONFIG_NAME

  override fun customizeDefaults(commonSettings: CommonCodeStyleSettings, indentOptions: IndentOptions) {
    commonSettings.SPACE_AROUND_ASSIGNMENT_OPERATORS = true
    commonSettings.SPACE_BEFORE_COMMA = false
    commonSettings.SPACE_AFTER_COMMA = true
    commonSettings.SPACE_BEFORE_COLON = false
    commonSettings.SPACE_AFTER_COLON = false
    commonSettings.SPACE_AROUND_EQUALITY_OPERATORS = false
    commonSettings.ALIGN_GROUP_FIELD_DECLARATIONS = false
  }

  override fun customizeSettings(consumer: CodeStyleSettingsCustomizable, settingsType: SettingsType): Unit = when (settingsType) {
    SettingsType.SPACING_SETTINGS -> {
      consumer.showStandardOptions(
        "SPACE_AROUND_ASSIGNMENT_OPERATORS",
        "SPACE_BEFORE_COMMA",
        "SPACE_AFTER_COMMA",
        "SPACE_BEFORE_COLON",
        "SPACE_AFTER_COLON"
      )

      consumer.renameStandardOption("SPACE_AROUND_ASSIGNMENT_OPERATORS", EditorConfigBundle.get("code.style.space.around.separator"))
      consumer.moveStandardOption("SPACE_BEFORE_COLON", CodeStyleSettingsCustomizableOptions.getInstance().SPACES_AROUND_OPERATORS)
      consumer.moveStandardOption("SPACE_AFTER_COLON", CodeStyleSettingsCustomizableOptions.getInstance().SPACES_AROUND_OPERATORS)
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
