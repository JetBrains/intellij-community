// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.undo

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.impl.UndoManagerImpl
import com.intellij.openapi.command.undo.DocumentReference
import com.intellij.openapi.command.undo.DocumentReferenceManager
import com.intellij.openapi.command.undo.GlobalUndoableAction
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileSystem
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
import java.io.IOException
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.function.Predicate
import java.util.function.Supplier
import kotlin.test.assertEquals

/**
 * `UndoManagerImpl.clearDocumentReferences` is the cleanup that an owner of a light document calls on dispose,
 * as `EditorTextField` does. After it, the undo history of other documents must keep no reference to the light file.
 * The undo history keeps the stack of a local file only while the stack holds a group.
 * A deleted file is not in the VFS tree, so after its deletion only a leak can keep it.
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

  @Test
  fun `a directory deleted outside a command is not kept`() {
    val deleted = timeoutRunBlocking(context = Dispatchers.EDT) {
      createFileUndoProvider()
      val directory = createDirectory("deleted-outside-command")
      delete(directory)
      directory
    }
    assertUndoDoesNotKeep(deleted)
  }

  @Test
  fun `a directory whose group leaves at the global limit is not kept`() {
    val deleted = timeoutRunBlocking(context = Dispatchers.EDT) {
      val directories = (0..UndoManagerImpl.getGlobalUndoLimit()).map { index ->
        val directory = createDirectory("trimmed-$index")
        val action = DirectoryAction(DocumentReferenceManager.getInstance().create(directory))
        val command = Runnable {
          undoManager(project.get()).undoableActionPerformed(action)
        }
        CommandProcessor.getInstance().executeCommand(project.get(), command, "change trimmed-$index", null)
        directory
      }
      undoManager(project.get()).flushCurrentCommandMerger()
      delete(directories.first())
      directories.first()
    }
    assertUndoDoesNotKeep(deleted)
  }

  @Test
  fun `a directory with a change that cannot be undone is not kept`() {
    val deleted = timeoutRunBlocking(context = Dispatchers.EDT) {
      val directory = createDirectory("non-undoable")
      val reference = DocumentReferenceManager.getInstance().create(directory)
      val command = Runnable {
        undoManager(project.get()).nonundoableActionPerformed(reference, true)
      }
      CommandProcessor.getInstance().executeCommand(project.get(), command, "non-undoable", null)
      undoManager(project.get()).flushCurrentCommandMerger()
      delete(directory)
      directory
    }
    assertUndoDoesNotKeep(deleted)
  }

  @Test
  fun `a check of the undo availability does not keep the file of the editor`() {
    val deleted = timeoutRunBlocking(context = Dispatchers.EDT) {
      val file = WriteAction.compute<VirtualFile, IOException> {
        sourceRoot.get().virtualFile.createChildData(this, "checked.txt")
      }
      val document = checkNotNull(FileDocumentManager.getInstance().getDocument(file))
      undoManager(project.get()).isUndoAvailable(StateRecordingEditor(file, document, 0))
      delete(file)
      file
    }
    assertUndoDoesNotKeep(deleted)
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

  /**
   * Runs an empty command. The first command of a project creates `FileUndoProvider`, because it is a project listener of commands.
   */
  private fun createFileUndoProvider() {
    CommandProcessor.getInstance().executeCommand(project.get(), {}, "create FileUndoProvider", null)
  }

  private fun createDirectory(name: String): VirtualFile {
    return WriteAction.compute<VirtualFile, IOException> {
      sourceRoot.get().virtualFile.createChildDirectory(this, name)
    }
  }

  /**
   * Deletes [file] outside a command, as a VFS refresh does after an external deletion.
   */
  private fun delete(file: VirtualFile) {
    WriteAction.run<IOException> {
      file.delete(this)
    }
  }

  /**
   * Checks that the undo manager does not keep [file].
   * The walk does not enter a static field, the application, a project or a file system, because they keep the file in other caches.
   * It does not enter a segment of the VFS cache either, because a segment can keep the data of a deleted file for some time.
   */
  private fun assertUndoDoesNotKeep(file: VirtualFile) {
    val roots = Supplier<Map<Any, String>> {
      mapOf(undoManager(project.get()) to "the undo manager")
    }
    val insideUndo = Predicate<Field> { field ->
      !Modifier.isStatic(field.modifiers) &&
      !ComponentManager::class.java.isAssignableFrom(field.type) &&
      !VirtualFileSystem::class.java.isAssignableFrom(field.type) &&
      field.type.name != VFS_CACHE_SEGMENT
    }
    val isFile = Predicate<VirtualFile> { candidate ->
      candidate === file
    }
    LeakHunter.checkLeak(roots, VirtualFile::class.java, insideUndo, isFile)
  }
}

private const val VFS_CACHE_SEGMENT = $$"com.intellij.openapi.vfs.newvfs.impl.VfsData$Segment"

/**
 * A global change of one directory that undo and redo do nothing for.
 * It is a top-level class, so it keeps no test instance.
 */
private class DirectoryAction(reference: DocumentReference) : GlobalUndoableAction(reference) {
  override fun undo() {
  }

  override fun redo() {
  }
}
