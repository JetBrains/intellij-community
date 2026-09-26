// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.psi.AbstractFileViewProvider
import com.intellij.psi.FileThreadingContracts
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.source.CharTableImpl
import com.intellij.psi.impl.source.DummyHolder
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil
import com.intellij.psi.impl.source.tree.CompositePsiElement
import com.intellij.psi.impl.source.tree.FileElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.impl.source.tree.TreeElement
import com.intellij.psi.tree.IElementType
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Verifies the IJPL-249128 contract: a non-physical (light) PSI file that has been exposed to a real editor must obey the
 * write-lock contract like a physical file, while a non-exposed light file may still be modified off the write lock.
 */
@TestApplication
class ExposedLightFilePsiWriteLockTest {
  private companion object {
    val project = projectFixture()

    // Single reusable IElementTypes, so repeated test runs don't exhaust the global IElementType registry.
    val LEAF_TYPE: IElementType = IElementType("ExposedLightFileTestLeaf", null)
    val COMPOSITE_TYPE: IElementType = IElementType("ExposedLightFileTestComposite", null)
  }

  @Test
  fun `exposed non-physical file must not be modified without write lock`() {
    val file = createNonPhysicalFile()
    assertFalse(file.isPhysical, "the file must be non-physical for this scenario")
    val vFile = assertInstanceOf(LightVirtualFile::class.java, file.viewProvider.virtualFile)

    // Simulate exposure to a real editor (exactly what EditorFactoryImpl.markLightFileExposedInEditor does).
    FileThreadingContracts.markLightFileRequiresApplicationLockOnModification(vFile)

    assertThreadingErrorLogged { mutateOffWriteLock(file) }
  }

  @Test
  fun `non-exposed non-physical file may be modified without write lock`() {
    val file = createNonPhysicalFile()
    assertFalse(file.isPhysical, "the file must be non-physical for this scenario")
    assertNoLoggedError { mutateOffWriteLock(file) }
  }

  @Test
  fun `dummy holder tree stays exempt even off write lock`() {
    // A DummyHolder-backed tree is exempt regardless of any exposure marker (condition 1 in isNonPhysicalOrInjected).
    assertNoLoggedError {
      // Build and mutate inside a read action: tree operations require read access, but not the write lock.
      runReadActionBlocking {
        val composite = compositeInDummyHolder(leaf("a"))
        composite.replaceChild(composite.firstChildNode, leaf("b"))
      }
    }
  }

  @Test
  fun `exposed non-physical file must not have its view provider dropped without write lock`() {
    val file = createNonPhysicalFile()
    val vFile = assertInstanceOf(LightVirtualFile::class.java, file.viewProvider.virtualFile)
    FileThreadingContracts.markLightFileRequiresApplicationLockOnModification(vFile)

    // FileManagerImpl.setViewProvider(vFile, null) drops the PSI provider; for an exposed light file this needs the write lock.
    assertThreadingErrorLogged {
      runReadActionBlocking { fileManager().setViewProvider(vFile, null) }
    }
  }

  @Test
  fun `non-exposed non-physical file view provider may be dropped without write lock`() {
    val file = createNonPhysicalFile()
    val vFile = file.viewProvider.virtualFile
    assertNoLoggedError {
      runReadActionBlocking { fileManager().setViewProvider(vFile, null) }
    }
  }

  private fun createNonPhysicalFile(): PsiFile =
    PsiFileFactory.getInstance(project.get())
      .createFileFromText("a.txt", PlainTextLanguage.INSTANCE, "hello", /*eventSystemEnabled=*/ false, /*markAsCopy=*/ false)!!

  private fun fileManager() = PsiManagerEx.getInstanceEx(project.get()).fileManager

  /** Performs a PSI tree change that reaches [com.intellij.psi.impl.source.tree.CompositeElement.subtreeChanged] without a write lock. */
  private fun mutateOffWriteLock(file: PsiFile) {
    runReadActionBlocking {
      val root = file.node as FileElement
      root.replaceChild(root.firstChildNode, leaf("A"))
    }
  }

  private fun leaf(text: String): TreeElement = withDummyHolder(object : LeafPsiElement(LEAF_TYPE, text) {
    override fun toString() = text
  })

  private fun compositeInDummyHolder(vararg children: TreeElement): CompositePsiElement {
    val composite = withDummyHolder(object : CompositePsiElement(COMPOSITE_TYPE) {
      override fun toString() = getChildren(null).asList().toString()
    }) as CompositePsiElement
    children.forEach(composite::addChild)
    return composite
  }

  private fun withDummyHolder(e: TreeElement): TreeElement {
    DummyHolder(PsiManager.getInstance(project.get()), e, null, CharTableImpl())
    CodeEditUtil.setNodeGenerated(e, true)
    return e
  }

  private fun assertThreadingErrorLogged(body: () -> Unit) {
    // A single PSI change may fire subtreeChanged (and thus the assertion) more than once, so tolerate multiple errors.
    val messages = collectLoggedErrors(body)
    assertTrue(messages.any { it.contains("Threading assertion") }, "expected a 'Threading assertion' error, got: $messages")
  }

  private fun assertNoLoggedError(body: () -> Unit) {
    val messages = collectLoggedErrors(body)
    assertTrue(messages.isEmpty()) { "unexpected logged error(s): $messages" }
  }

  private fun collectLoggedErrors(body: () -> Unit): List<String> {
    val messages = ArrayList<String>()
    LoggedErrorProcessor.executeWith<RuntimeException?>(object : LoggedErrorProcessor() {
      override fun processError(category: String, message: String, details: Array<String?>, t: Throwable?): MutableSet<Action?> {
        messages.add(message)
        return Action.NONE
      }
    }) { body() }
    return messages
  }
}
