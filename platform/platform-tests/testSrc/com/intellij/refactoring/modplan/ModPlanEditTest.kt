// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan

import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModCreateFile
import com.intellij.modcommand.ModDeleteFile
import com.intellij.modcommand.ModMoveFile
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.light.LightElement
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.util.concurrent.Callable

class ModPlanEditTest : BasePlatformTestCase() {
  private val logEntries = ArrayList<String>()

  /** The directory of the project: an anchor that has no file, so the engine takes the file of the first edit. */
  private fun root(): PsiElement = PsiManager.getInstance(project).findDirectory(myFixture.tempDirFixture.getFile("/")!!)!!

  private fun toCommand(vararg edits: ModPlanEdit): ModCommand? {
    return PlatformTestUtil.waitForFuture(ApplicationManager.getApplication().executeOnPooledThread(Callable {
      runCatching {
        runReadActionBlocking {
          val plan = ModPlan.createPlan(edits.toList(), ModPlanConflicts(emptyList()), emptyList(), "test", "test", root(), false)
          (plan as? ModPlan.AcceptedPlan)?.execution?.command
        }
      }
    })).getOrThrow()
  }

  /** An element of no file, or a light element of the given physical source. */
  private class TestLight(manager: PsiManager, source: PsiElement?) : LightElement(manager, PlainTextLanguage.INSTANCE) {
    init {
      source?.let { navigationElement = it }
    }

    override fun getText(): String = "light"

    override fun toString(): String = "TestLight"
  }

  /** A physical file that has another physical file as its navigation element. */
  private class FileWithNavigation(private val file: PsiFile, private val navigation: PsiElement) : PsiFile by file {
    override fun getNavigationElement(): PsiElement = navigation
  }

  fun `test an update whose element another update removed is skipped`() {
    val a = myFixture.addFileToProject("a.txt", "a")
    val leaf = a.firstChild

    val command = toCommand(
      ModPlanEdit.update(a) { (it.firstChild as LeafElement).replaceWithText("b") },
      ModPlanEdit.update(leaf) { logEntries.add("never") },
    )

    assertNotNull(command)
    assertEquals(emptyList<String>(), logEntries)
  }

  fun `test a delete of an element that an update removed is skipped`() {
    val a = myFixture.addFileToProject("a.txt", "a")
    val leaf = a.firstChild

    val command = toCommand(
      ModPlanEdit.update(a) { (it.firstChild as LeafElement).replaceWithText("b") },
      ModPlanEdit.delete(leaf),
    )

    assertNotNull(command)
  }

  fun `test two updates of one file see their own elements`() {
    val a = myFixture.addFileToProject("a.txt", "a")
    val copies = ArrayList<PsiElement>()

    val command = toCommand(
      ModPlanEdit.update(a) { copies.add(it); logEntries.add("first") },
      ModPlanEdit.update(a) { copies.add(it); logEntries.add("second") },
    )

    assertNotNull(command)
    assertEquals(listOf("first", "second"), logEntries)
    assertEquals(2, copies.size)
    assertTrue(copies.all { !it.isPhysical && it.containingFile.name == "a.txt" })
  }

  fun `test update spanning two files gives both copies in one command`() {
    val a = myFixture.addFileToProject("a.txt", "a")
    val b = myFixture.addFileToProject("b.txt", "b")
    val copies = ArrayList<PsiElement>()

    val command = toCommand(ModPlanEdit.update(listOf(a, b)) { copies.addAll(it) })

    assertNotNull(command)
    assertEquals(listOf("a.txt", "b.txt"), copies.map { it.containingFile.name })
    assertTrue(copies.all { !it.isPhysical })
  }

  fun `test two elements of one file in an update come from one copy`() {
    val a = myFixture.addFileToProject("a.txt", "a")
    val copies = ArrayList<PsiElement>()

    val command = toCommand(ModPlanEdit.update(listOf(a, a.firstChild)) { copies.addAll(it) })

    assertNotNull(command)
    assertSame(copies[0], copies[1].containingFile)
  }

  fun `test update strictly inside a deleted element is dropped`() {
    val a = myFixture.addFileToProject("d/a.txt", "a")
    val directory = a.containingDirectory!!

    val command = toCommand(ModPlanEdit.update(a) { logEntries.add("update") }, ModPlanEdit.delete(directory))

    assertEmpty(logEntries)
    assertEquals(listOf(directory.virtualFile), command!!.unpack().filterIsInstance<ModDeleteFile>().map { it.file() })
  }

  fun `test update of the deleted element itself runs`() {
    val a = myFixture.addFileToProject("a.txt", "a")

    val command = toCommand(ModPlanEdit.update(a) { logEntries.add("update") }, ModPlanEdit.delete(a))

    assertEquals(listOf("update"), logEntries)
    assertEquals(listOf(a.virtualFile), command!!.unpack().filterIsInstance<ModDeleteFile>().map { it.file() })
  }

  fun `test delete inside a deleted directory is dropped`() {
    val a = myFixture.addFileToProject("d/a.txt", "a")
    val directory = a.containingDirectory!!

    val command = toCommand(ModPlanEdit.delete(a), ModPlanEdit.delete(directory))

    assertEquals(listOf(directory.virtualFile), command!!.unpack().filterIsInstance<ModDeleteFile>().map { it.file() })
  }

  fun `test two deletes of one file give one write`() {
    val a = myFixture.addFileToProject("a.txt", "a")

    val command = toCommand(ModPlanEdit.delete(a), ModPlanEdit.delete(a))

    assertEquals(listOf(a.virtualFile), command!!.unpack().filterIsInstance<ModDeleteFile>().map { it.file() })
  }

  fun `test an update inside a deleted file is dropped`() {
    val a = myFixture.addFileToProject("a.txt", "a")

    val command = toCommand(ModPlanEdit.update(a.firstChild) { logEntries.add("never") }, ModPlanEdit.delete(a))

    assertEmpty(logEntries)
    assertEquals(listOf(a.virtualFile), command!!.unpack().filterIsInstance<ModDeleteFile>().map { it.file() })
  }

  fun `test create and move become ModCreateFile and ModMoveFile`() {
    val a = myFixture.addFileToProject("d/a.txt", "a")
    val directory = a.containingDirectory!!.virtualFile

    val command = toCommand(ModPlanEdit.Create(directory, "n.txt", "text"), ModPlanEdit.Move(a.virtualFile, directory, "m.txt"))

    val parts = command!!.unpack()
    assertEquals(listOf("n.txt"), parts.filterIsInstance<ModCreateFile>().map { it.file().name })
    assertEquals(listOf("m.txt"), parts.filterIsInstance<ModMoveFile>().map { it.targetFile().name })
  }

  fun `test a physical element is not replaced by its navigation element`() {
    val a = myFixture.addFileToProject("a.txt", "a")
    val b = myFixture.addFileToProject("b.txt", "b")

    val command = toCommand(ModPlanEdit.delete(FileWithNavigation(a, b)))

    assertEquals(listOf(a.virtualFile), command!!.unpack().filterIsInstance<ModDeleteFile>().map { it.file() })
  }

  fun `test a delete of a light element deletes its source`() {
    val a = myFixture.addFileToProject("a.txt", "a")

    val command = toCommand(ModPlanEdit.delete(TestLight(a.manager, a)))

    assertEquals(listOf(a.virtualFile), command!!.unpack().filterIsInstance<ModDeleteFile>().map { it.file() })
  }

  fun `test an update of a light element whose source is of another class gives null`() {
    val a = myFixture.addFileToProject("a.txt", "a")

    val command = toCommand(ModPlanEdit.update(TestLight(a.manager, a)) { logEntries.add("never") })

    assertNull(command)
    assertEmpty(logEntries)
  }

  fun `test an edit of an element without a file gives null`() {
    val a = myFixture.addFileToProject("a.txt", "a")
    val noFile = TestLight(a.manager, null)

    assertNull(toCommand(ModPlanEdit.delete(noFile), ModPlanEdit.update(a) { logEntries.add("never") }))
    assertNull(toCommand(ModPlanEdit.update(a) { logEntries.add("never") }, ModPlanEdit.update(noFile) { logEntries.add("never") }))
    assertEmpty(logEntries)
  }

  fun `test an update of a directory does not become the anchor of the command`() {
    val a = myFixture.addFileToProject("d/a.txt", "a")
    val directory = a.containingDirectory!!

    val command = toCommand(ModPlanEdit.update(directory) { logEntries.add("directory") }, ModPlanEdit.update(a) { logEntries.add("file") })

    assertNotNull(command)
    assertEquals(setOf("directory", "file"), logEntries.toSet())
  }

  fun `test an edit built earlier still applies after another edit in the file`() {
    val a = myFixture.addFileToProject("a.txt", "a")
    val b = myFixture.addFileToProject("b.txt", "b")
    val edit = runReadActionBlocking { ModPlanEdit.update(b) { logEntries.add("update") } }
    WriteCommandAction.runWriteCommandAction(project) {
      PsiDocumentManager.getInstance(project).getDocument(a)!!.insertString(0, "x")
      PsiDocumentManager.getInstance(project).commitAllDocuments()
    }

    val command = toCommand(edit)

    assertNotNull(command)
    assertEquals(listOf("update"), logEntries)
  }

  fun `test an edit whose element is gone gives null`() {
    val a = myFixture.addFileToProject("a.txt", "a")
    val b = myFixture.addFileToProject("b.txt", "b")
    val edit = runReadActionBlocking { ModPlanEdit.update(b) { logEntries.add("never") } }
    WriteCommandAction.runWriteCommandAction(project) { b.delete() }

    assertNull(toCommand(edit, ModPlanEdit.update(a) { logEntries.add("never") }))
    assertEmpty(logEntries)
  }

  fun `test no edits of PSI give no command`() {
    assertTrue(toCommand()!!.isEmpty)
  }

  fun `test the edits run in the given order`() {
    val a = myFixture.addFileToProject("a.txt", "a")
    val b = myFixture.addFileToProject("b.txt", "b")

    val command = toCommand(
      ModPlanEdit.update(b) { logEntries.add("first") },
      ModPlanEdit.update(a) { logEntries.add("second") },
      ModPlanEdit.update(b) { logEntries.add("third") },
    )

    assertNotNull(command)
    assertEquals(listOf("first", "second", "third"), logEntries)
  }
}
