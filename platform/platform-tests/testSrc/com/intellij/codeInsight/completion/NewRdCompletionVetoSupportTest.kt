// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion

import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.util.PsiUtilBase
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.disposableFixture
import com.intellij.testFramework.junit5.fixture.editorFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class NewRdCompletionVetoSupportTest {
  private companion object {
    val project = projectFixture()
    val module = project.moduleFixture()
    val sourceRoot = module.sourceRootFixture()
    val file = sourceRoot.psiFileFixture("file.txt", "abcde")
  }

  private val editor = file.editorFixture()
  private val disposable = disposableFixture()

  @BeforeEach
  fun setUp() {
    ExtensionTestUtil.maskExtensions(ExtensionPointName("com.intellij.newRdCompletionVeto"), listOf(EditorReadingVeto()), disposable.get())
  }

  @Test
  fun `veto that reads the editor works on a background thread`() = timeoutRunBlocking(context = Dispatchers.Default) {
    assertFalse(NewRdCompletionVetoSupport.isAllowed(editor.get()))
  }

  @Test
  fun `veto that reads the editor works on EDT`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    assertFalse(NewRdCompletionVetoSupport.isAllowed(editor.get()))
  }

  /**
   * Follows the example in the [NewRdCompletionVeto] KDoc: it reads the caret through [PsiUtilBase.getPsiFileInEditor].
   */
  private class EditorReadingVeto : NewRdCompletionVeto {
    override fun veto(editor: Editor): Boolean {
      val project = editor.project ?: return false
      return PsiUtilBase.getPsiFileInEditor(editor, project) != null
    }
  }
}
