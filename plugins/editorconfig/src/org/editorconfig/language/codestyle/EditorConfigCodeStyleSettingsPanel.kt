// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codestyle

import com.intellij.application.options.TabbedLanguageCodeStylePanel
import com.intellij.psi.codeStyle.CodeStyleSettings
import org.editorconfig.language.EditorConfigLanguage

class EditorConfigCodeStyleSettingsPanel(currentSettings: CodeStyleSettings, settings: CodeStyleSettings)
  : TabbedLanguageCodeStylePanel(EditorConfigLanguage, currentSettings, settings) {

  override fun initTabs(settings: CodeStyleSettings?) {
    addSpacesTab(settings)
    addWrappingAndBracesTab(settings)
  }
}
