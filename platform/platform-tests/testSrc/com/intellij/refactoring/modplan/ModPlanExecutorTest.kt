// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan

import com.intellij.modcommand.FutureVirtualFile
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiManager
import com.intellij.psi.SmartPointerManager
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.refactoring.listeners.RefactoringElementListenerProvider
import com.intellij.refactoring.listeners.RefactoringEventData
import com.intellij.refactoring.listeners.RefactoringEventListener
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesProcessor
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.containers.ContainerUtil

/** The created directories, the moves and the element listeners of [ModPlanExecutor]. */
class ModPlanExecutorTest : BasePlatformTestCase() {
  private val events = ArrayList<String>()

  override fun setUp() {
    super.setUp()
    project.messageBus.connect(testRootDisposable).subscribe(RefactoringEventListener.REFACTORING_EVENT_TOPIC, object : RefactoringEventListener {
      override fun refactoringStarted(refactoringId: String, beforeData: RefactoringEventData?) {
        events.add("started $refactoringId")
      }

      override fun refactoringDone(refactoringId: String, afterData: RefactoringEventData?) {
        events.add("done $refactoringId")
      }

      override fun undoRefactoring(refactoringId: String) {
        events.add("undo $refactoringId")
      }

      override fun redoRefactoring(refactoringId: String) {
        events.add("redo $refactoringId")
      }
    })
  }

  fun `test the undo and the redo of the writes publish the events of the refactoring`() {
    val a = addFile("a.txt", "a")
    val execution = execution(ModPlanEdit.Move(a, myFixture.tempDirFixture.findOrCreateDir("target"), "a.txt"))
    TestDialogManager.setTestDialog(TestDialog.OK, testRootDisposable)

    assertEquals(ModPlanExecutionResult.Applied, ModPlanExecutor.execute(execution, true))
    UndoManager.getInstance(project).undo(null)
    UndoManager.getInstance(project).redo(null)

    assertEquals(listOf("started refactoring.test", "done refactoring.test", "undo refactoring.test", "redo refactoring.test"), events)
    assertEquals("target", a.parent.name)
  }

  fun `test creates a file without changing an existing file`() {
    val directory = addFile("a.txt", "a").parent

    val execution = execution(ModPlanEdit.Create(directory, "n.txt", "x"))

    assertEquals(ModPlanExecutionResult.Applied, ModPlanExecutor.execute(execution, true))
    assertEquals("x", textOf(directory.findChild("n.txt")!!))
  }

  fun `test moves to a new nested directory and undoes the whole operation`() {
    val a = addFile("a.txt", "a")
    val root = a.parent
    val target = FutureVirtualFile(root, "target", null)
    val nested = FutureVirtualFile(target, "nested", null)
    val execution = execution(ModPlanEdit.CreateDirectory(target), ModPlanEdit.CreateDirectory(nested), ModPlanEdit.Move(a, nested, "b.txt"))
    TestDialogManager.setTestDialog(TestDialog.OK, testRootDisposable)

    assertNull(root.findChild("target"))
    assertEquals(ModPlanExecutionResult.Applied, ModPlanExecutor.execute(execution, true))
    assertEquals("a", textOf(root.findFileByRelativePath("target/nested/b.txt")!!))
    assertNull(root.findChild("a.txt"))

    UndoManager.getInstance(project).undo(null)
    assertNull(root.findChild("target"))
    assertEquals("a", textOf(root.findChild("a.txt")!!))
  }

  fun `test a directory move rejects changes to a child`() {
    val a = addFile("source/a.txt", "a")
    val directory = a.parent
    val execution = execution(ModPlanEdit.Move(directory, directory.parent, "target"))

    edit(a, "edited a")

    assertInstanceOf(ModPlanExecutor.execute(execution, true), ModPlanExecutionResult.Stale::class.java)
    assertEquals("source", directory.name)
    assertEmpty(events)
  }

  fun `test a directory move rejects a new child`() {
    val a = addFile("source/a.txt", "a")
    val directory = a.parent
    val execution = execution(ModPlanEdit.Move(directory, directory.parent, "target"))

    addFile("source/b.txt", "b")

    assertInstanceOf(ModPlanExecutor.execute(execution, true), ModPlanExecutionResult.Stale::class.java)
    assertEquals("source", directory.name)
    assertEmpty(events)
  }

  fun `test a directory snapshot does not load child documents`() {
    val directory = myFixture.tempDirFixture.findOrCreateDir("source")
    val child = WriteAction.compute<VirtualFile, RuntimeException> {
      directory.createChildData(this, "unopened.txt").also { it.setBinaryContent("original".toByteArray()) }
    }
    val loadedFiles = ContainerUtil.createConcurrentList<VirtualFile>()
    val connection = ApplicationManager.getApplication().messageBus.connect(testRootDisposable)
    connection.subscribe(FileDocumentManagerListener.TOPIC, object : FileDocumentManagerListener {
      override fun fileContentLoaded(file: VirtualFile, document: Document) {
        loadedFiles.add(file)
      }
    })

    val execution = execution(ModPlanEdit.Move(directory, directory.parent, "target"))
    connection.disconnect()

    // A cached document is held weakly, so its absence depends on the garbage collector; a load is an event.
    assertDoesntContain(loadedFiles, child)
    edit(child, "changed after planning")
    assertInstanceOf(ModPlanExecutor.execute(execution, true), ModPlanExecutionResult.Stale::class.java)
    assertEquals("source", directory.name)
  }

  fun `test a move rejects a renamed source`() {
    val a = addFile("a.txt", "a")
    val execution = execution(ModPlanEdit.Move(a, a.parent, "target.txt"))

    WriteCommandAction.runWriteCommandAction(project) { a.rename(this, "renamed.txt") }

    assertEquals(ModPlanExecutionResult.Stale(listOf(a)), ModPlanExecutor.execute(execution, true))
    assertEquals("renamed.txt", a.name)
    assertEmpty(events)
  }

  fun `test a move rejects a read-only destination before writing`() {
    val a = addFile("a.txt", "a")
    val target = addFile("target/marker.txt", "marker").parent
    val nested = FutureVirtualFile(target, "nested", null)
    val execution = execution(ModPlanEdit.CreateDirectory(nested), ModPlanEdit.Move(a, nested, "a.txt"))

    WriteAction.runAndWait<Exception> { target.isWritable = false }
    try {
      assertEquals(ModPlanExecutionResult.ReadOnly(listOf(target)), ModPlanExecutor.execute(execution, true))
      assertEquals("a", textOf(a))
      assertNull(target.findChild("nested"))
      assertEmpty(events)
    }
    finally {
      WriteAction.runAndWait<Exception> { target.isWritable = true }
    }
  }

  fun `test a renamed destination ancestor makes a move stale`() {
    val source = addFile("source/a.txt", "a")
    val destination = myFixture.tempDirFixture.findOrCreateDir("target/nested")
    val execution = execution(ModPlanEdit.Move(source, destination, "a.txt"))

    WriteAction.runAndWait<Exception> { destination.parent.rename(this, "renamed") }

    assertEquals(ModPlanExecutionResult.Stale(listOf(destination)), ModPlanExecutor.execute(execution, true))
    assertEquals("source", source.parent.name)
    assertEmpty(events)
  }

  fun `test a renamed ancestor makes a creation in a future directory stale`() {
    val ancestor = myFixture.tempDirFixture.findOrCreateDir("target")
    val directory = FutureVirtualFile(ancestor, "nested", null)
    val execution = execution(ModPlanEdit.CreateDirectory(directory), ModPlanEdit.Create(directory, "a.txt", "a"))

    WriteAction.runAndWait<Exception> { ancestor.rename(this, "renamed") }

    assertEquals(ModPlanExecutionResult.Stale(listOf(ancestor)), ModPlanExecutor.execute(execution, true))
    assertNull(ancestor.findChild("nested"))
    assertEmpty(events)
  }

  fun `test another child of the destination does not make a move stale`() {
    val source = addFile("source/a.txt", "a")
    val destination = myFixture.tempDirFixture.findOrCreateDir("target")
    val execution = execution(ModPlanEdit.Move(source, destination, "a.txt"))

    addFile("target/other.txt", "other")

    assertEquals(ModPlanExecutionResult.Applied, ModPlanExecutor.execute(execution, true))
    assertEquals(destination, source.parent)
  }

  fun `test the listeners and the events of a move are those of the legacy move`() {
    val listenerEntries = ArrayList<String>()
    logListeners(listenerEntries)
    val legacySource = myFixture.addFileToProject("legacy/source/a.txt", "a")
    val legacyTarget = psiManager.findDirectory(myFixture.tempDirFixture.findOrCreateDir("legacy/target"))!!
    MoveFilesOrDirectoriesProcessor(project, arrayOf(legacySource), legacyTarget, false, false, null, null).run()
    val legacyEntries = listenerEntries.toList()
    listenerEntries.clear()

    val source = myFixture.addFileToProject("modplan/source/a.txt", "a")
    val target = psiManager.findDirectory(myFixture.tempDirFixture.findOrCreateDir("modplan/target"))!!
    val execution = execution(ModPlanEdit.Move(source.virtualFile, target.virtualFile, "a.txt"))
    // The events of MoveFilesOrDirectoriesProcessor: getBeforeData, getAfterData and the listener of every moved element.
    val result = ModPlanExecutor.execute(execution, true) {
      val movedFile = SmartPointerManager.createPointer<PsiElement>(source)
      val beforeData = RefactoringEventData().apply {
        addElements(arrayOf<PsiElement>(source))
        addTargetElement(target)
      }
      ModPlanRefactoring.WriteEvent(beforeData, listOf(ModPlanRefactoring.ElementChange(source, ModPlanRefactoring.ElementChange.Kind.MOVED, movedFile::getElement)),
                       { RefactoringEventData().apply { addElement(target) } })
    }

    assertEquals(ModPlanExecutionResult.Applied, result)
    assertEquals(listOf("started a.txt to target", "listener of a.txt", "moved to target/a.txt", "done target"), legacyEntries)
    assertEquals(legacyEntries, listenerEntries)
  }

  fun `test an element that is not found after the writes does not fail them`() {
    val source = myFixture.addFileToProject("source.txt", "source")
    val other = myFixture.addFileToProject("other.txt", "other")
    val listenerEntries = ArrayList<String>()
    logListeners(listenerEntries)
    val execution = execution(ModPlanEdit.Create(source.virtualFile.parent, "created.txt", "created"))

    val result = ModPlanExecutor.execute(execution, true) {
      ModPlanRefactoring.WriteEvent(null, listOf(ModPlanRefactoring.ElementChange(source, ModPlanRefactoring.ElementChange.Kind.RENAMED) { null },
                                    ModPlanRefactoring.ElementChange(other, ModPlanRefactoring.ElementChange.Kind.RENAMED) { other }), { null })
    }

    assertEquals(ModPlanExecutionResult.Applied, result)
    assertEquals(listOf("started", "listener of source.txt", "listener of other.txt", "renamed to other.txt", "done"), listenerEntries)
  }

  fun `test a stale plan asks for no events`() {
    val source = myFixture.addFileToProject("source.txt", "source")
    val execution = execution(ModPlanEdit.Move(source.virtualFile, source.virtualFile.parent, "target.txt"))
    edit(source.virtualFile, "changed")

    val result = ModPlanExecutor.execute(execution, true) { error("A stale plan must not ask for the events") }

    assertInstanceOf(result, ModPlanExecutionResult.Stale::class.java)
    assertEmpty(events)
  }

  /** Logs the refactoring events and the calls of the element listeners into [entries], by the names of the elements. */
  private fun logListeners(entries: MutableList<String>) {
    project.extensionArea.getExtensionPoint(RefactoringElementListenerProvider.EP_NAME).registerExtension(
      RefactoringElementListenerProvider { element ->
        entries.add("listener of ${nameOf(element)}")
        object : RefactoringElementListener {
          override fun elementMoved(newElement: PsiElement) {
            entries.add("moved to ${nameOf(newElement.parent)}/${nameOf(newElement)}")
          }

          override fun elementRenamed(newElement: PsiElement) {
            entries.add("renamed to ${nameOf(newElement)}")
          }
        }
      }, testRootDisposable,
    )
    project.messageBus.connect(testRootDisposable).subscribe(RefactoringEventListener.REFACTORING_EVENT_TOPIC, object : RefactoringEventListener {
      override fun refactoringStarted(refactoringId: String, beforeData: RefactoringEventData?) {
        val elements = beforeData?.getUserData(RefactoringEventData.PSI_ELEMENT_ARRAY_KEY).orEmpty().map { nameOf(it) }
        val target = beforeData?.getUserData(RefactoringEventData.TARGET_ELEMENT_KEY)?.let { " to ${nameOf(it)}" }.orEmpty()
        entries.add(("started " + elements.joinToString()).trim() + target)
      }

      override fun refactoringDone(refactoringId: String, afterData: RefactoringEventData?) {
        entries.add(("done " + afterData?.getUserData(RefactoringEventData.PSI_ELEMENT_KEY)?.let { nameOf(it) }.orEmpty()).trim())
      }
    })
  }

  private fun nameOf(element: PsiElement?): String = (element as? PsiFileSystemItem)?.name.orEmpty()

  private fun addFile(name: String, text: String): VirtualFile = myFixture.addFileToProject(name, text).virtualFile

  /** The execution of [edits], planned as a refactoring plans it: in a read action on a pooled thread. */
  private fun execution(vararg edits: ModPlanEdit): ModPlanExecution {
    val plan = inBackground {
      val anchor = PsiManager.getInstance(project).findDirectory(myFixture.tempDirFixture.getFile("/")!!)!!
      ModPlan.createPlan(edits.toList(), ModPlanConflicts.EMPTY, "Test refactoring", "refactoring.test", anchor, false)
    }
    return (plan as ModPlan.AcceptedPlan).execution
  }

  private fun textOf(file: VirtualFile): String = FileDocumentManager.getInstance().getDocument(file)!!.text

  private fun edit(file: VirtualFile, newText: String) {
    val document = FileDocumentManager.getInstance().getDocument(file)!!
    WriteCommandAction.runWriteCommandAction(project) {
      document.setText(newText)
      PsiDocumentManager.getInstance(project).commitDocument(document)
    }
  }
}
