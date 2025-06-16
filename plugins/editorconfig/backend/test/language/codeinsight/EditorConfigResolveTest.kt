// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.language.codeinsight

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.intellij.psi.util.PsiUtilCore
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class EditorConfigResolveTest : BasePlatformTestCase() {
  override fun getTestDataPath() =
    "${PathManagerEx.getCommunityHomePath()}/plugins/editorconfig/testData/org/editorconfig/language/codeinsight/resolve/"

  fun testSubcaseHeader() = doTest()
  fun testSiblingHeader() = doTest()
  fun testDeclarationReference() = doTest()
  fun testInsideSection() = doTest()
  fun testIgnoreCase() = doTest()

  fun doTest() {
    val name = getTestName(true)
    myFixture.configureByFile("$name/.editorconfig")
    val answerFilePath = "$testDataPath$name/resolve.txt"
    val result = getSerializedResolveResults()
    UsefulTestCase.assertSameLinesWithFile(answerFilePath, result, true)
  }

  private fun getSerializedResolveResults(): String {
    val file = myFixture.file
    val referenceList = getReferencesInFile(file)
    val editor = myFixture.editor
    val sb = StringBuilder()

    for (reference in referenceList) {
      val targetElement = reference.resolve() ?: continue
      val sourceElement = reference.element
      val referenceRange = reference.rangeInElement
      val sourceElementText = sourceElement.text
      val sourceElementOffset = sourceElement.node.startOffset
      val targetElementNode = targetElement.node

      sb
        .append("'")
        .append(referenceRange.subSequence(sourceElementText))
        .append("'")
        .append(" at ")
        .append(editor.offsetToLogicalPosition(referenceRange.startOffset + sourceElementOffset))
        .append(" =>")
        .append('\n')
        .append("  ")
        .append(PsiUtilCore.getElementType(targetElementNode))
        .append(" at ")
        .append(editor.offsetToLogicalPosition(targetElementNode.startOffset))
        .append('\n')
    }
    return sb.toString()
  }

  private fun getReferencesInFile(file: PsiFile): List<PsiReference> {
    val referencesList = mutableListOf<PsiReference>()
    file.accept(object : PsiElementVisitor() {
      override fun visitElement(element: PsiElement) {
        referencesList.addAll(listOf(*element.references))
        element.acceptChildren(this)
      }
    })
    referencesList.sortBy { it.element.textRange.startOffset }
    return referencesList
  }
}
