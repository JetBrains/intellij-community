// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codestyle

import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CustomCodeStyleSettings
import org.editorconfig.language.EditorConfigLanguage

class EditorConfigCodeStyleSettings(container: CodeStyleSettings)
  : CustomCodeStyleSettings(EditorConfigLanguage.id, container)
