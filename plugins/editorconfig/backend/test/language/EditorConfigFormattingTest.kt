// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class EditorConfigFormattingTest : BasePlatformTestCase() {
  override fun getTestDataPath() = "${PathManagerEx.getCommunityHomePath()}/plugins/editorconfig/testData/org/editorconfig/language/formatting"

  private fun doTestWithSettings(settings: CommonCodeStyleSettings.() -> Unit) {
    val file = myFixture.configureByFile("${getTestName(true)}/before/.editorconfig")

    val languageSettings = CodeStyle.getLanguageSettings(file, EditorConfigLanguage)
    languageSettings.settings()

    WriteCommandAction.runWriteCommandAction(project) {
      CodeStyleManager.getInstance(project).reformat(file)
    }

    myFixture.checkResultByFile("${getTestName(true)}/after/.editorconfig")
  }

  fun testAlignOnValues() = doTestWithSettings { ALIGN_GROUP_FIELD_DECLARATIONS = true }

  fun testNoAlignment() = doTestWithSettings { }

  fun testSpaceAroundComma() = doTestWithSettings {
    SPACE_AFTER_COMMA = true
    SPACE_BEFORE_COMMA = true
  }

  fun testKeepSpacesAroundComma() = doTestWithSettings {
    SPACE_AFTER_COMMA = false
    SPACE_BEFORE_COMMA = false
  }
}
