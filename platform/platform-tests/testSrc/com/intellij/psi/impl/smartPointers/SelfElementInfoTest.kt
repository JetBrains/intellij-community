// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.smartPointers

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlText
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
internal class SelfElementInfoTest {
  private companion object {
    val project = projectFixture()
    val module = project.moduleFixture()
    val sourceRoot = module.sourceRootFixture()
    val file = sourceRoot.psiFileFixture("file.xml", "<root>one</root>")
  }


  /**
   * AI-generated test.
   *
   * An anchor-based pointer and a type-based pointer for the same element must compare as equal.
   */
  @Test
  fun anchorAndTypePointersPointToTheSameElement(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val psiFile = file.get()
    val manager = SmartPointerManager.getInstance(project.get())
    val typePointer = readAction {
      manager.createSmartPsiElementPointer(PsiTreeUtil.findChildOfType(psiFile, XmlText::class.java)!!)
    }
    val element = readAction { typePointer.element!! }
    (typePointer as SmartPsiElementPointerImpl<*>).incrementAndGetReferenceCount(-1)
    SmartPointerAnchorProvider.EP_NAME.point.registerExtension(object : SmartPointerAnchorProvider() {
      override fun getAnchor(candidate: PsiElement): PsiElement? = candidate.takeIf { it === element }

      override fun restoreElement(anchor: PsiElement): PsiElement = anchor
    }, disposable)
    val anchorPointer = readAction {
      manager.createSmartPsiElementPointer(element)
    }

    assertNotSame(typePointer, anchorPointer)
    assertTrue(manager.pointToTheSameElement(typePointer, anchorPointer))
  }
}
