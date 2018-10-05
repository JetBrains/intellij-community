// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codestyle

import com.intellij.lang.Language
import com.intellij.openapi.options.Configurable
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettingsProvider
import com.intellij.psi.codeStyle.CustomCodeStyleSettings
import org.editorconfig.language.EditorConfigLanguage

class EditorConfigCodeStyleSettingsProvider : CodeStyleSettingsProvider() {
  override fun createCustomSettings(settings: CodeStyleSettings): CustomCodeStyleSettings =
    EditorConfigCodeStyleSettings(settings)

  override fun createSettingsPage(settings: CodeStyleSettings, originalSettings: CodeStyleSettings): Configurable =
    EditorConfigCodeStyleConfigurable(settings, originalSettings)

  override fun getConfigurableDisplayName(): String = "EditorConfig"

  override fun getLanguage(): Language = EditorConfigLanguage
}
