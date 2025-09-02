// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codestyle

import com.intellij.editorconfig.common.syntax.EditorConfigLanguage
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CustomCodeStyleSettings

class EditorConfigCodeStyleSettings(container: CodeStyleSettings)
  : CustomCodeStyleSettings(EditorConfigLanguage.id, container)
