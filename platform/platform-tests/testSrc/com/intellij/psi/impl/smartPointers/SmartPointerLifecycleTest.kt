// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.smartPointers

import com.intellij.lang.xml.XMLLanguage
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlText
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.utils.vfs.createFile
import com.intellij.util.ref.GCUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.ref.WeakReference

/**
 * Lifecycle cases of the smart pointer subsystem that the main suite
 * (`com.intellij.java.psi.impl.smartPointers.SmartPsiElementPointersTest`) does not cover:
 * file deletion and recreation, directory pointers, hard pointers, tracker GC, and the
 * degenerate forms of [com.intellij.psi.SmartPsiFileRange].
 *
 * See `docs/IntelliJ-Platform/4_man/Lang/PSI/Smart-Pointers.md` for the specification.
 */
@TestApplication
internal class SmartPointerLifecycleTest {
  private companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
    private val moduleFixture = projectFixture.moduleFixture("SmartPointerLifecycleTest")
    private val sourceRootFixture = moduleFixture.sourceRootFixture()
  }

  private val project get() = projectFixture.get()
  private val manager get() = SmartPointerManager.getInstance(project)

  /** A pointer to a deleted file resolves neither the element nor the file. */
  @Test
  fun `element pointer does not resolve after its file is deleted`() = timeoutRunBlocking {
    val psiFile = createXmlFile("deleted.xml")
    val pointer = readAction {
      manager.createSmartPsiElementPointer(PsiTreeUtil.findChildOfType(psiFile, XmlText::class.java)!!)
    }
    assertNotNull(readAction { pointer.element })

    val vFile = psiFile.virtualFile
    writeAction { vFile.delete(this) }

    readAction {
      assertNull(pointer.element, "A pointer must not resolve after its file is deleted")
      assertNull(pointer.containingFile, "The containing file is gone with the virtual file")
    }
  }

  /**
   * The stored virtual file is re-resolved through `parent.findChild(name)`
   * ([SelfElementInfo.restoreVFile]), so a pointer survives a delete-and-recreate of a file
   * with the same name — as long as it was not resolved while the file was missing.
   */
  @Test
  fun `pointer resolves again after the file is recreated with the same name`() = timeoutRunBlocking {
    val text = "<root>text</root>"
    val psiFile = createXmlFile("recreated.xml", text)
    val pointer = readAction {
      manager.createSmartPsiElementPointer(PsiTreeUtil.findChildOfType(psiFile, XmlText::class.java)!!)
    }
    assertEquals("text", readAction { pointer.element!!.text })

    val vFile = psiFile.virtualFile
    writeAction { vFile.delete(this) }
    val recreated = writeAction {
      sourceRootVFile().createFile("recreated.xml").also { VfsUtil.saveText(it, text) }
    }

    readAction {
      val restored = pointer.element
      assertNotNull(restored, "The pointer must resolve into the recreated file")
      assertEquals("text", restored!!.text)
      assertEquals(recreated, restored.containingFile.virtualFile)
    }
  }

  /** A directory pointer re-finds the [com.intellij.psi.PsiDirectory] by virtual file and has no range. */
  @Test
  fun `directory pointer resolves and survives rename`() = timeoutRunBlocking {
    val vDir = writeAction { sourceRootVFile().createChildDirectory(this, "pointedDir") }
    val pointer = readAction {
      manager.createSmartPsiElementPointer(PsiManager.getInstance(project).findDirectory(vDir)!!)
    }
    readAction {
      assertSame(PsiManager.getInstance(project).findDirectory(vDir), pointer.element)
      assertNull(pointer.range, "A directory has no range")
      assertNull(pointer.psiRange, "A directory has no range")
    }

    writeAction { vDir.rename(this, "renamedDir") }

    readAction {
      val restored = pointer.element
      assertNotNull(restored, "A rename keeps the virtual file valid, so the pointer must survive")
      assertEquals("renamedDir", restored!!.name)
    }
  }

  @Test
  fun `directory pointer does not resolve after the directory is deleted`() = timeoutRunBlocking {
    val vDir = writeAction { sourceRootVFile().createChildDirectory(this, "doomedDir") }
    val pointer = readAction {
      manager.createSmartPsiElementPointer(PsiManager.getInstance(project).findDirectory(vDir)!!)
    }
    assertNotNull(readAction { pointer.element })

    writeAction { vDir.delete(this) }

    readAction {
      assertNull(pointer.element, "A pointer must not resolve after its directory is deleted")
    }
  }

  /**
   * An element of a non-physical file with no document cannot be tracked by offsets, so the
   * pointer holds the element hard: it resolves to the identical instance and is not registered
   * in the file's tracker.
   */
  @Test
  fun `element in a non-physical file without a document is held hard`() = timeoutRunBlocking {
    readAction {
      val file = PsiFileFactory.getInstance(project)
        .createFileFromText("hard.xml", XMLLanguage.INSTANCE, "<root>text</root>", false, false)
      val text = PsiTreeUtil.findChildOfType(file, XmlText::class.java)!!
      val pointer = manager.createSmartPsiElementPointer(text)

      assertTrue(pointer.toString().startsWith("hard{"), "Expected a hard pointer, got $pointer")
      assertSame(text, pointer.element, "A hard pointer resolves to the identical element")
      assertEquals(text.textRange, pointer.range)
      assertEquals(0, (manager as SmartPointerManagerImpl).getPointersNumber(file),
                   "A hard pointer must not be registered in the tracker")
    }
  }

  /**
   * The tracker references pointers weakly: once a pointer is unreachable, a GC plus
   * [SmartPointerTracker.processQueue] removes it from the tracker. The element's pointer
   * cache holds the pointer softly, which must not keep it tracked either.
   */
  @Test
  fun `tracker stops tracking a collected pointer`() = timeoutRunBlocking {
    val psiFile = createXmlFile("gc.xml")
    val impl = manager as SmartPointerManagerImpl
    val ref = createPointerAndForget(psiFile)
    assertEquals(1, impl.getPointersNumber(psiFile))

    GCUtil.tryGcSoftlyReachableObjects { ref.get() == null }
    assertNull(ref.get(), "Nothing but the tracker and the element cache holds the pointer, so it must be collectible")

    SmartPointerTracker.processQueue()
    assertEquals(0, impl.getPointersNumber(psiFile), "A collected pointer must leave the tracker")
  }

  /**
   * A range pointer over the whole file degrades to a file pointer
   * ([SmartPsiFileRangePointerImpl.createElementInfo]): after the file grows, it reports the
   * new whole-document range, not the originally captured endpoints.
   */
  @Test
  fun `whole-file range pointer tracks the whole document`() = timeoutRunBlocking {
    val psiFile = createXmlFile("whole.xml", "<root>text</root>")
    val pointer = readAction { manager.createSmartPsiFileRangePointer(psiFile, psiFile.textRange) }

    withContext(Dispatchers.EDT) {
      WriteCommandAction.runWriteCommandAction(project) {
        val document = FileDocumentManager.getInstance().getDocument(psiFile.virtualFile)!!
        document.insertString(document.textLength, "<!-- more -->")
        PsiDocumentManager.getInstance(project).commitDocument(document)
      }
    }

    readAction {
      val document = FileDocumentManager.getInstance().getDocument(psiFile.virtualFile)!!
      val range = pointer.range
      assertNotNull(range, "A whole-file range pointer must stay valid")
      assertEquals(TextRange(0, document.textLength), TextRange.create(range!!),
                   "A whole-file range pointer reports the current whole document")
      assertSame(psiFile, pointer.element)
    }
  }

  /**
   * A deletion that swallows a pointer's range kills the range for good, while the file stays
   * alive: `getElement()` of a [com.intellij.psi.SmartPsiFileRange] is then null even though
   * `getContainingFile()` still restores the file.
   */
  @Test
  fun `range pointer dies when a deletion swallows its range`() = timeoutRunBlocking {
    val psiFile = createXmlFile("swallowed.xml", "<root>text</root>")
    // "text" occupies offsets 6..10
    val pointer = readAction { manager.createSmartPsiFileRangePointer(psiFile, TextRange(6, 10)) }
    assertNotNull(readAction { pointer.range })

    withContext(Dispatchers.EDT) {
      WriteCommandAction.runWriteCommandAction(project) {
        val document = FileDocumentManager.getInstance().getDocument(psiFile.virtualFile)!!
        document.deleteString(5, 11)
        PsiDocumentManager.getInstance(project).commitDocument(document)
      }
    }

    readAction {
      assertTrue(psiFile.isValid, "The file itself survives the edit")
      assertNull(pointer.range, "The range must not survive a deletion that covers it")
      assertNull(pointer.element, "A file range pointer with a dead range must not resolve")
      assertNotNull(pointer.containingFile, "The containing file is still restorable")
    }
  }

  private fun sourceRootVFile() = sourceRootFixture.get().virtualFile

  private suspend fun createXmlFile(name: String, text: String = "<root>text</root>"): PsiFile {
    val vFile = writeAction {
      sourceRootVFile().createFile(name).also { VfsUtil.saveText(it, text) }
    }
    return readAction { PsiManager.getInstance(project).findFile(vFile)!! }
  }

  private suspend fun createPointerAndForget(psiFile: PsiFile): WeakReference<*> = readAction {
    WeakReference(manager.createSmartPsiElementPointer(PsiTreeUtil.findChildOfType(psiFile, XmlText::class.java)!!))
  }
}
