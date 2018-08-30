// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase

class EditorConfigFormattingTest : LightPlatformCodeInsightFixtureTestCase() {
  override fun getHomePath() = "/plugins/editorconfig/testSrc/org/editorconfig/language/formatting/"

  fun testAlignOnValues() {
    val name = getTestName(true)
    val beforePath = "$homePath$name/before/.editorconfig"
    val afterPath = "$homePath$name/after/.editorconfig"

    myFixture.configureByFile(beforePath)
    val file = myFixture.file
    val languageSettings = CodeStyle.getLanguageSettings(file, EditorConfigLanguage)
    languageSettings.ALIGN_GROUP_FIELD_DECLARATIONS = true
    WriteCommandAction.runWriteCommandAction(project) {
      CodeStyleManager.getInstance(project).reformat(file)
    }
    myFixture.checkResultByFile(afterPath)
  }

  fun testNoAlignment() {
    val name = getTestName(true)
    val beforePath = "$homePath$name/before/.editorconfig"
    val afterPath = "$homePath$name/after/.editorconfig"
    myFixture.configureByFiles(beforePath)
    val file = myFixture.file
    WriteCommandAction.runWriteCommandAction(project) {
      CodeStyleManager.getInstance(project).reformat(file)
    }
    myFixture.checkResultByFile(afterPath)
  }
}
