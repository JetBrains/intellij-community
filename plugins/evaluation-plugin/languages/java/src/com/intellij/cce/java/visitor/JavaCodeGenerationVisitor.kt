// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.java.visitor

import com.intellij.cce.core.*
import com.intellij.cce.visitor.EvaluationVisitor
import com.intellij.cce.visitor.exceptions.PsiConverterException
import com.intellij.psi.*
import com.intellij.psi.util.startOffset

class JavaCodeGenerationVisitor : EvaluationVisitor, JavaRecursiveElementVisitor() {
  private var codeFragment: CodeFragment? = null

  override val language: Language = Language.JAVA
  override val feature: String = "code-generation"

  override fun getFile(): CodeFragment = codeFragment
                                         ?: throw PsiConverterException("Invoke 'accept' with visitor on PSI first")

  override fun visitJavaFile(file: PsiJavaFile) {
    codeFragment = CodeFragment(file.textOffset, file.textLength)
    super.visitJavaFile(file)
  }

  override fun visitMethod(method: PsiMethod) {
    codeFragment?.addChild(
      CodeToken(method.text, method.startOffset, SimpleTokenProperties.create(TypeProperty.METHOD, SymbolLocation.PROJECT) {})
    )
    val body = method.body
    if (body != null) {
      val meaningfullBodyChildren = body.trim()
      if (meaningfullBodyChildren.any()) {
        val firstMeaningfulChildren = meaningfullBodyChildren.first()
        val meaningfullBodyText = meaningfullBodyChildren.map { it.text }.joinToString("")

        codeFragment?.addChild(
          CodeToken(meaningfullBodyText, firstMeaningfulChildren.startOffset, SimpleTokenProperties.create(TypeProperty.METHOD_BODY, SymbolLocation.PROJECT) {})
        )
      }
    }
  }

  private fun PsiCodeBlock.trim(): List<PsiElement> {
    val firstIndex = children.indexOfFirst { it.isMeaningful()}
    val lastIndex = children.indexOfLast { it.isMeaningful() }
    val indexRange = (firstIndex.. lastIndex)
    return children.filterIndexed { index, it ->
      it is PsiExpressionStatement
      index in indexRange
    }
  }

  private fun PsiElement.isMeaningful(): Boolean {
    if (this is PsiWhiteSpace) {
      return false
    }
    if (this is PsiJavaToken) {
      return tokenType != JavaTokenType.LBRACE && tokenType != JavaTokenType.RBRACE
    }
    return true
  }
}