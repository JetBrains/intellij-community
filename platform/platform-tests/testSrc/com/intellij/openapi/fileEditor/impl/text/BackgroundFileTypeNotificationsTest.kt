// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.text

import com.intellij.ide.highlighter.ArchiveFileType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.AccessToken
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.backgroundWriteAction
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.useBackgroundWriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerBase
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.fileTypes.ExactFileNameMatcher
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.eel.fs.EelFiles
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.fileEditorManagerFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

@TestApplication
internal class BackgroundFileTypeNotificationsTest {
  private val projectFixture: TestFixture<Project> = projectFixture()
  private val managerFixture: TestFixture<FileEditorManagerImpl> = projectFixture.fileEditorManagerFixture()
  private val directoryFixture: TestFixture<Path> = tempPathFixture()

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `validity changes immediately and the editor closes on EDT`(
    backgroundNotification: Boolean,
    @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking {
    if (backgroundNotification) assumeTrue(useBackgroundWriteAction)
    val project = projectFixture.get()
    val manager = managerFixture.get()
    val application = ApplicationManager.getApplication()
    val directory = directoryFixture.get()
    val path = withContext(Dispatchers.IO) {
      EelFiles.writeString(directory.resolve("background-file-type-notifications.txt"), "text")
    }
    val file = withContext(Dispatchers.EDT) { LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)!! }
    val changed = CompletableDeferred<Unit>()
    val closed = CompletableDeferred<Unit>()
    project.messageBus.connect(disposable).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
      override fun fileClosed(source: FileEditorManager, closedFile: VirtualFile) {
        if (closedFile == file) {
          application.assertIsDispatchThread()
          closed.complete(Unit)
        }
      }
    })
    val editor = withContext(Dispatchers.EDT) {
      manager.openFile(file, true).filterIsInstance<TextEditor>().single().also {
        it.addPropertyChangeListener { event ->
          if (event.propertyName == FileEditor.getPropValid()) {
            application.assertIsDispatchThread()
            assertTrue(event.oldValue as Boolean)
            assertFalse(event.newValue as Boolean)
            changed.complete(Unit)
          }
        }
      }
    }
    val fileTypes = FileTypeManager.getInstance() as FileTypeManagerImpl
    val matcher = ExactFileNameMatcher(file.name)
    var token: AccessToken? = null
    try {
      AsyncEditorLoader.awaitLoaded(editor.editor)
      edtWriteAction {
        token = fileTypes.deferFileTypesChanged()
        fileTypes.associate(ArchiveFileType.INSTANCE, matcher)
        (FileDocumentManager.getInstance() as FileDocumentManagerBase).clearDocumentCache()
        assertFalse(FileDocumentManager.getInstance().canHaveDocument(file))
        assertTrue(editor.isValid)
      }
      val finishNotifications = {
        assertEquals(!backgroundNotification, application.isDispatchThread)
        token!!.finish()
        token = null
        assertFalse(editor.isValid)
        assertFalse(changed.isCompleted)
        assertFalse(closed.isCompleted)
      }
      if (backgroundNotification) {
        backgroundWriteAction { finishNotifications() }
      }
      else {
        edtWriteAction { finishNotifications() }
      }
      changed.await()
      closed.await()
      withContext(Dispatchers.EDT) { assertFalse(manager.isFileOpen(file)) }
    }
    finally {
      edtWriteAction {
        token?.finish()
        manager.closeFile(file)
        fileTypes.removeAssociation(ArchiveFileType.INSTANCE, matcher)
      }
    }
  }
}
