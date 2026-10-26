// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.language

import com.intellij.grazie.grazieTestFixture
import com.intellij.grazie.jlanguage.Lang
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.ui.TextFieldWithAutoCompletion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test


@TestApplication
class PlainTextSupportTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val grazie by grazieTestFixture(projectFixture)

  @Test
  fun `test grammar check in file`() {
    grazie.enableProofreadingFor(setOf(Lang.RUSSIAN))
    grazie.runHighlightTestForFile("ide/language/plain/Example.txt")
  }

  @Test
  fun `test no grammar checks in TextFieldWithAutoCompletion`(): Unit = timeoutRunBlocking {
    val field = withContext(Dispatchers.EDT) {
      val field = TextFieldWithAutoCompletion(
        grazie.project, TextFieldWithAutoCompletion.StringsCompletionProvider(emptyList(), null), true, "I have an new apple here.")
      field.addNotify()
      field
    }
    try {
      grazie.fixture.configureFromExistingVirtualFile(readAction { FileDocumentManager.getInstance().getFile(field.editor!!.document)!! })
      grazie.fixture.checkHighlighting()
    }
    finally {
      withContext(Dispatchers.EDT) {
        field.removeNotify()
      }
    }
  }
}
