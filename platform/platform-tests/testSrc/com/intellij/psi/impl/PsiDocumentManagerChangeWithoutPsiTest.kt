// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl

import com.intellij.openapi.application.EDT
import com.intellij.openapi.command.writeCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.concurrency.ThreadingAssertions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Checks that [PsiDocumentManagerBase.documentChanged] ends the pending state of a document that has no PSI.
 *
 * A document that is not write-thread-only accepts a change without a lock.
 * For such a document, the change must not keep its events in [PsiDocumentManagerBase], and it must not need a write action.
 * A write-thread-only document must also keep no events after a change in a write command.
 */
@TestApplication
internal class PsiDocumentManagerChangeWithoutPsiTest {

  private companion object {
    private const val CHANGE_COUNT = 10

    private val project = projectFixture(openAfterCreation = true)
  }

  private val documentManager: PsiDocumentManagerBase
    get() = PsiDocumentManager.getInstance(project.get()) as PsiDocumentManagerBase

  @Test
  fun `a change without a lock keeps no pending events of a document without PSI`(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    val document = EditorFactory.getInstance().createDocument("", false, true)
    assertFalse((document as DocumentImpl).isWriteThreadOnly, "The document must accept a change without a lock")
    assertNull(FileDocumentManager.getInstance().getFile(document), "The document must have no file and no PSI")

    ThreadingAssertions.assertNoReadAccess()
    repeat(CHANGE_COUNT) { index ->
      document.insertString(document.textLength, "line $index\n")
      assertNoPendingState(document)
    }
  }

  @Test
  fun `a change in a write command keeps no pending events of a document without PSI`(): Unit = timeoutRunBlocking {
    val document = withContext(Dispatchers.EDT) {
      EditorFactory.getInstance().createDocument("")
    }
    assertTrue((document as DocumentImpl).isWriteThreadOnly, "The document must accept a change only in a write action")

    repeat(CHANGE_COUNT) { index ->
      writeCommandAction(project.get(), "") {
        document.insertString(document.textLength, "line $index\n")
      }
      assertNoPendingState(document)
    }
  }

  private fun assertNoPendingState(document: Document) {
    assertEquals(0, documentManager.getUncommittedEventCount(document), "A change of a document without PSI must keep no events")
    assertTrue(documentManager.isCommitted(document), "A document without PSI must be committed after a change")
    assertEquals(document.text, documentManager.getLastCommittedText(document).toString(),
                 "The last committed text must be the current text of a document without PSI")
  }
}
