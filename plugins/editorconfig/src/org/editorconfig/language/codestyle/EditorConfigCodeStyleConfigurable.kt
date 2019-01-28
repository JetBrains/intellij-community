// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codestyle

import com.intellij.application.options.CodeStyleAbstractConfigurable
import com.intellij.application.options.CodeStyleAbstractPanel
import com.intellij.psi.codeStyle.CodeStyleSettings

class EditorConfigCodeStyleConfigurable(settings: CodeStyleSettings, originalSettings: CodeStyleSettings)
  : CodeStyleAbstractConfigurable(settings, originalSettings, "EditorConfig") {

  override fun createPanel(settings: CodeStyleSettings): CodeStyleAbstractPanel =
    EditorConfigCodeStyleSettingsPanel(currentSettings, settings)

  override fun getHelpTopic(): String? = null
}
