// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Covers the `--wait` (`$EDITOR`/`$VISUAL`) contract of [CommandLineWaitingManager]: closing the editor of a waited file
 * must flush its unsaved edits to disk *before* the CLI caller is released, so the caller never reads stale content
 * (IJPL-35398).
 */
class CommandLineWaitingManagerTest : BasePlatformTestCase() {
  fun `test closing a waited file flushes unsaved edits before releasing the caller`() {
    val fileDocumentManager = FileDocumentManager.getInstance()
    val file = myFixture.addFileToProject("wait-me.txt", "before").virtualFile

    val future = CommandLineWaitingManager.getInstance().addHookForFile(file)
    assertFalse("the wait hook must stay pending until the file is closed", future.isDone)

    val document = fileDocumentManager.getDocument(file)!!
    WriteCommandAction.runWriteCommandAction(project) { document.setText("after") }
    assertTrue("precondition: the edited document is unsaved", fileDocumentManager.isDocumentUnsaved(document))

    fireFileClosed(file)

    assertTrue("closing a waited file must release the CLI caller", future.isDone)
    assertFalse("closing a waited file must flush its edits to disk", fileDocumentManager.isDocumentUnsaved(document))
    assertEquals("after", String(file.contentsToByteArray()))
  }

  fun `test closing a file without a wait hook does not force a save`() {
    val fileDocumentManager = FileDocumentManager.getInstance()
    val file = myFixture.addFileToProject("no-wait.txt", "before").virtualFile

    val document = fileDocumentManager.getDocument(file)!!
    WriteCommandAction.runWriteCommandAction(project) { document.setText("after") }
    assertTrue("precondition: the edited document is unsaved", fileDocumentManager.isDocumentUnsaved(document))

    fireFileClosed(file)

    assertTrue("a file with no --wait hook must keep the platform's normal (deferred) save behavior on close",
               fileDocumentManager.isDocumentUnsaved(document))

    // keep the fixture from tearing down with a dirty document
    WriteCommandAction.runWriteCommandAction(project) { fileDocumentManager.saveDocument(document) }
  }

  private fun fireFileClosed(file: VirtualFile) {
    ApplicationManager.getApplication().messageBus
      .syncPublisher(FileEditorManagerListener.FILE_EDITOR_MANAGER)
      .fileClosed(FileEditorManager.getInstance(project), file)
  }
}
