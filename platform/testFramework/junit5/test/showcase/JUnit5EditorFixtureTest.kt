// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.showcase

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@TestApplication
class JUnit5EditorFixtureTest {

  private companion object {
    val project = projectFixture()
    val module = project.moduleFixture()
    val sourceRoot = module.sourceRootFixture()
    val file = sourceRoot.psiFileFixture("file.txt", "abcde")
  }

  private val localEditor = file.editorFixture()


  @Test
  fun `content of editors`() {
    Assertions.assertEquals("abcde", localEditor.get().document.text)
  }

  @Test
  fun `caret position in editors`() {
    runBlocking {
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          localEditor.get().caretModel.moveToOffset(2)
          Assertions.assertEquals(2, localEditor.get().caretModel.offset)
        }
      }
    }
  }

  @Test
  fun `selection in editors`() {
    runBlocking {
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          localEditor.get().selectionModel.setSelection(1, 3)
          Assertions.assertEquals("bc", localEditor.get().selectionModel.selectedText)
        }
      }
    }
  }
}