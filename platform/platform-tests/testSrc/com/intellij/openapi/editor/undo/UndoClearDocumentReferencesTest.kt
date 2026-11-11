// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.undo

import com.intellij.openapi.application.EDT
import com.intellij.openapi.command.undo.DocumentReferenceManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.testFramework.LeakHunter
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.junit5.fixture.virtualFileFixture
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
    val module = project.moduleFixture()
    val sourceRoot = module.sourceRootFixture()
  }

  private val physicalFile = sourceRoot.virtualFileFixture("physical.txt", "")

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

  @Test
  fun `a cleanup after the flush releases the light file of a mention`() {
    val lightName = "mention-cleared-after-flush.txt"
    val physical = timeoutRunBlocking(context = Dispatchers.EDT) {
      checkNotNull(FileDocumentManager.getInstance().getDocument(physicalFile.get()))
    }
    val lightStackSizes = timeoutRunBlocking(context = Dispatchers.EDT) {
      val lightDocument = changeWhileLightTextEditorIsCurrent(LightVirtualFile(lightName, ""), physical)
      undoManager(project.get()).flushCurrentCommandMerger()
      val beforeCleanup = stackSize(lightDocument)
      undoManager(project.get()).clearDocumentReferences(lightDocument)
      beforeCleanup to stackSize(lightDocument)
    }
    val physicalStackSize = timeoutRunBlocking(context = Dispatchers.EDT) {
      stackSize(physical)
    }
    assertEquals(1 to 0, lightStackSizes)
    assertEquals(1, physicalStackSize)
    assertLightFileNotReachable(lightName, physical)
  }

  private fun assertCleanupReleasesLightFile(lightName: String, flushBeforeCleanup: Boolean) {
    val changed = timeoutRunBlocking(context = Dispatchers.EDT) {
      val changed = EditorFactory.getInstance().createDocument("")
      changeWhileLightFileIsCurrent(lightName, changed, flushBeforeCleanup)
      undoManager(project.get()).flushCurrentCommandMerger()
      changed
    }
    val stackSize = timeoutRunBlocking(context = Dispatchers.EDT) {
      stackSize(changed)
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
   * Changes [changed] in a command while a text editor of [lightFile] is current, and returns the light document.
   * The command mentions the light document, because a text editor of it is current.
   * The editor has no project, so the code style cache of the project does not keep the light file.
   */
  private fun changeWhileLightTextEditorIsCurrent(lightFile: LightVirtualFile, changed: Document): Document {
    val lightDocument = checkNotNull(FileDocumentManager.getInstance().getDocument(lightFile))
    val editor = EditorFactory.getInstance().createEditor(lightDocument)
    try {
      val textEditor = TextEditorProvider.getInstance().getTextEditor(editor)
      undoManager(project.get()).withCurrentEditor(textEditor) {
        typeSpace(project.get(), changed)
      }
    } finally {
      EditorFactory.getInstance().releaseEditor(editor)
    }
    return lightDocument
  }

  private fun stackSize(document: Document): Int {
    val reference = DocumentReferenceManager.getInstance().create(document)
    return undoManager(project.get()).getStackSize(reference, true)
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
