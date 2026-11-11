// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.undo

import com.intellij.openapi.application.EDT
import com.intellij.openapi.command.undo.DocumentReferenceManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.testFramework.LeakHunter
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.function.Supplier
import kotlin.test.assertEquals

/**
 * `UndoManagerImpl.clearDocumentReferences` is the cleanup that an owner of a light document calls on dispose,
 * as `EditorTextField` does. After it, the undo history of other documents must keep no reference to the light file.
 */
@TestApplication
internal class UndoClearDocumentReferencesTest {
  private companion object {
    val project = projectFixture(openAfterCreation = true)
  }

  @AfterEach
  fun dropHistory() {
    timeoutRunBlocking(context = Dispatchers.EDT) {
      undoManager(project.get()).dropHistoryInTests()
    }
  }

  @Test
  fun `a cleanup after the flush releases the light file of an editor state`() {
    assertCleanupReleasesLightFile("state-cleared-after-flush.txt", true)
  }

  @Test
  fun `a cleanup before the flush releases the light file of an editor state`() {
    assertCleanupReleasesLightFile("state-cleared-before-flush.txt", false)
  }

  private fun assertCleanupReleasesLightFile(lightName: String, flushBeforeCleanup: Boolean) {
    val changed = timeoutRunBlocking(context = Dispatchers.EDT) {
      val changed = EditorFactory.getInstance().createDocument("")
      changeWhileLightFileIsCurrent(lightName, changed, flushBeforeCleanup)
      undoManager(project.get()).flushCurrentCommandMerger()
      changed
    }
    val stackSize = timeoutRunBlocking(context = Dispatchers.EDT) {
      val reference = DocumentReferenceManager.getInstance().create(changed)
      undoManager(project.get()).getStackSize(reference, true)
    }
    assertEquals(1, stackSize)
    assertLightFileNotReachable(lightName, changed)
  }

  /**
   * Changes [changed] in a command while an editor of a new light file is current, and then clears the light document.
   */
  private fun changeWhileLightFileIsCurrent(lightName: String, changed: Document, flushBeforeCleanup: Boolean) {
    val lightFile = LightVirtualFile(lightName, "")
    val lightDocument = checkNotNull(FileDocumentManager.getInstance().getDocument(lightFile))
    val editor = StateRecordingEditor(lightFile, lightDocument, 0)
    undoManager(project.get()).withCurrentEditor(editor) {
      typeSpace(project.get(), changed)
    }
    if (flushBeforeCleanup) {
      undoManager(project.get()).flushCurrentCommandMerger()
    }
    undoManager(project.get()).clearDocumentReferences(lightDocument)
  }

  /**
   * Checks the roots of [LeakHunter] and [changed]. The undo history of a document without a file lives in the document.
   */
  private fun assertLightFileNotReachable(lightName: String, changed: Document) {
    val roots = Supplier {
      val result = LeakHunter.allRoots().get()
      result[changed] = "the changed document"
      result
    }
    LeakHunter.checkLeak(roots, LightVirtualFile::class.java) { file ->
      file.name == lightName
    }
  }
}
