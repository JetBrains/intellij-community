// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan

import com.intellij.modcommand.FutureVirtualFile
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModCreateFile
import com.intellij.modcommand.ModDeleteFile
import com.intellij.modcommand.ModMoveFile
import com.intellij.modcommand.ModUpdateFileText
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.PsiManager
import com.intellij.psi.createSmartFileRange
import com.intellij.psi.impl.light.LightElement
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlText
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.util.concurrent.Callable

class ModPlanEditTest : BasePlatformTestCase() {
  private val logEntries = ArrayList<String>()

  /** The directory of the project: an anchor that has no file, so the engine takes the file of the first edit. */
  private fun root(): PsiElement = PsiManager.getInstance(project).findDirectory(myFixture.tempDirFixture.getFile("/")!!)!!

  private fun toCommand(vararg edits: ModPlanEdit, anchor: PsiElement? = null): ModCommand? {
    return PlatformTestUtil.waitForFuture(ApplicationManager.getApplication().executeOnPooledThread(Callable {
      runCatching {
        runReadActionBlocking {
          val plan = ModPlan.createPlan(edits.toList(), ModPlanConflicts(emptyList()), "test", "test", anchor ?: root(), false)
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

  fun `test creates destination directories without changing the project`() {
    val a = myFixture.addFileToProject("a.txt", "a")
    val parent = a.virtualFile.parent
    val destination = FutureVirtualFile(parent, "target", null)
    val nested = FutureVirtualFile(destination, "nested", null)

    val command = toCommand(
      ModPlanEdit.CreateDirectory(destination),
      ModPlanEdit.CreateDirectory(nested),
      ModPlanEdit.Move(a.virtualFile, nested, a.name),
    )!!

    assertNull(parent.findChild("target"))
    assertSame(parent, a.virtualFile.parent)
    assertEquals(listOf(destination, nested), command.unpack().filterIsInstance<ModCreateFile>().map { it.file() })
    assertTrue(command.unpack().filterIsInstance<ModCreateFile>().all { it.content() is ModCreateFile.Directory })
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

  fun `test an update with a binary anchor file uses the file of the first update`() {
    val binaryFile = myFixture.tempDirFixture.createFile("a.zip", "a")
    val a = myFixture.addFileToProject("a.txt", "a")
    val anchor = PsiManager.getInstance(project).findFile(binaryFile)!!
    assertTrue(binaryFile.fileType.isBinary)

    val command = toCommand(ModPlanEdit.update(a) { logEntries.add("file") }, anchor = anchor)

    assertNotNull(command)
    assertEquals(listOf("file"), logEntries)
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

  /** An edit of an injected fragment before an edit of its host: both are written into the one copy of the host. */
  fun `test an edit of an injected fragment and an edit of its host give one text of the host`() {
    InjectedLanguageManager.getInstance(project).registerMultiHostInjector(PlainTextInXmlTextInjector(), testRootDisposable)
    val xml = myFixture.addFileToProject("a.xml", "<a>one</a>") as XmlFile
    val fragment = InjectedLanguageManager.getInstance(project).findInjectedElementAt(xml, xml.text.indexOf("one"))!!.containingFile

    val command = toCommand(
      ModPlanEdit.update(fragment) { (it.firstChild as LeafElement).replaceWithText("two") },
      ModPlanEdit.update(xml) { (it as XmlFile).rootTag!!.setName("b") },
    )

    assertEquals(listOf("<b>two</b>"), command!!.unpack().filterIsInstance<ModUpdateFileText>().map { it.newText() })
  }

  /** Injects plain text into the whole text of an XML tag. */
  private class PlainTextInXmlTextInjector : MultiHostInjector {
    override fun getLanguagesToInject(registrar: MultiHostRegistrar, context: PsiElement) {
      val host = context as PsiLanguageInjectionHost
      registrar.startInjecting(PlainTextLanguage.INSTANCE)
      registrar.addPlace(null, null, host, TextRange(0, host.textLength))
      registrar.doneInjecting()
    }

    override fun elementsToInjectIn(): List<Class<out PsiElement>> = listOf(XmlText::class.java)
  }

  /** @return the replacement of the first [oldText] of [file] with [newText] */
  private fun replaceText(file: PsiFile, oldText: String, newText: String): ModPlanEdit = runReadActionBlocking {
    val start = file.text.indexOf(oldText)
    ModPlanEdit.ReplaceText(file.createSmartFileRange(TextRange(start, start + oldText.length))!!, newText)
  }

  private fun newTextsOf(command: ModCommand?): List<String> = command!!.unpack().filterIsInstance<ModUpdateFileText>().map { it.newText() }

  fun `test a plan of one text replacement writes it`() {
    val a = myFixture.addFileToProject("a.txt", "one two three")

    assertEquals(listOf("one 2 three"), newTextsOf(toCommand(replaceText(a, "two", "2"))))
  }

  fun `test two text replacements in one file are both written`() {
    val a = myFixture.addFileToProject("a.txt", "one two three")

    assertEquals(listOf("one 2 3"), newTextsOf(toCommand(replaceText(a, "two", "2"), replaceText(a, "three", "3"))))
  }

  fun `test a text replacement follows a PSI edit before it in the same file`() {
    val xml = myFixture.addFileToProject("a.xml", "<a>one</a><!--two-->") as XmlFile

    val command = toCommand(replaceText(xml, "two", "2"), ModPlanEdit.update(xml) { (it as XmlFile).rootTag!!.setName("abc") })

    assertEquals(listOf("<abc>one</abc><!--2-->"), newTextsOf(command))
  }

  fun `test a text replacement in a deleted file is skipped`() {
    val a = myFixture.addFileToProject("a.txt", "one two three")

    val command = toCommand(replaceText(a, "two", "2"), ModPlanEdit.delete(a))

    assertEmpty(newTextsOf(command))
    assertEquals(listOf(a.virtualFile), command!!.unpack().filterIsInstance<ModDeleteFile>().map { it.file() })
  }

  fun `test text replacements in two files write both files`() {
    val a = myFixture.addFileToProject("a.txt", "one")
    val b = myFixture.addFileToProject("b.txt", "two")

    assertEquals(setOf("1", "2"), newTextsOf(toCommand(replaceText(a, "one", "1"), replaceText(b, "two", "2"))).toSet())
  }

  /** @return an edit of the first [oldText] of [file]: it logs the text at the range, then writes [newText] there in the leaf of the copy */
  private fun updateRange(file: PsiFile, oldText: String, newText: String): ModPlanEdit = runReadActionBlocking {
    val start = file.text.indexOf(oldText)
    ModPlanEdit.UpdateRange(file.createSmartFileRange(TextRange(start, start + oldText.length))!!) { copy, range ->
      logEntries.add(range.substring(copy.text))
      val leaf = copy.findElementAt(range.startOffset)!!
      (leaf.node as LeafElement).replaceWithText(range.shiftLeft(leaf.textRange.startOffset).replace(leaf.text, newText))
    }
  }

  fun `test two range updates in one element both run at their text after the first makes it longer`() {
    val a = myFixture.addFileToProject("a.txt", "one two")

    val command = toCommand(updateRange(a, "one", "three"), updateRange(a, "two", "2"))

    assertEquals(listOf("one", "two"), logEntries)
    assertEquals(listOf("three 2"), newTextsOf(command))
  }

  fun `test a range update runs after an update replaces its element`() {
    val a = myFixture.addFileToProject("a.txt", "one two")

    val command = toCommand(ModPlanEdit.update(a.firstChild) { (it as LeafElement).replaceWithText("x one two") }, updateRange(a, "two", "2"))

    assertEquals(listOf("two"), logEntries)
    assertEquals(listOf("x one 2"), newTextsOf(command))
  }

  fun `test a range update whose text another update replaced is skipped`() {
    val a = myFixture.addFileToProject("a.txt", "one two three")

    val command = toCommand(ModPlanEdit.update(a.firstChild) { (it as LeafElement).replaceWithText("XYZ") }, updateRange(a, "two", "2"))

    assertEmpty(logEntries)
    assertEquals(listOf("XYZ"), newTextsOf(command))
  }

  fun `test a range update in an injected fragment follows an edit of its host`() {
    InjectedLanguageManager.getInstance(project).registerMultiHostInjector(PlainTextInXmlTextInjector(), testRootDisposable)
    val xml = myFixture.addFileToProject("a.xml", "<a>one two</a>") as XmlFile
    val fragment = runReadActionBlocking {
      InjectedLanguageManager.getInstance(project).findInjectedElementAt(xml, xml.text.indexOf("two"))!!.containingFile
    }

    val command = toCommand(ModPlanEdit.update(xml) { (it as XmlFile).rootTag!!.setName("abc") }, updateRange(fragment, "two", "2"))

    assertEquals(listOf("two"), logEntries)
    assertEquals(listOf("<abc>one 2</abc>"), newTextsOf(command))
  }

  fun `test a range update in a deleted file is skipped`() {
    val a = myFixture.addFileToProject("a.txt", "one two three")

    val command = toCommand(updateRange(a, "two", "2"), ModPlanEdit.delete(a))

    assertEmpty(logEntries)
    assertEquals(listOf(a.virtualFile), command!!.unpack().filterIsInstance<ModDeleteFile>().map { it.file() })
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
