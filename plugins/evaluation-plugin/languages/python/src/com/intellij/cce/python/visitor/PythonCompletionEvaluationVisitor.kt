// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.python.visitor

import com.intellij.cce.core.CodeFragment
import com.intellij.cce.core.CodeToken
import com.intellij.cce.core.Language
import com.intellij.cce.core.SimpleTokenProperties
import com.intellij.cce.core.SymbolLocation
import com.intellij.cce.core.TokenProperties
import com.intellij.cce.core.TypeProperty
import com.intellij.cce.visitor.EvaluationVisitor
import com.intellij.cce.visitor.exceptions.PsiConverterException
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import com.jetbrains.python.PythonTokenSetContributor
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyKeywordArgument
import com.jetbrains.python.psi.PyParameter
import com.jetbrains.python.psi.PyRecursiveElementVisitor
import com.jetbrains.python.psi.PyReferenceExpression

class PythonCompletionEvaluationVisitor : EvaluationVisitor, PyRecursiveElementVisitor() {
  private var _codeFragment: CodeFragment? = null
  private val tokenSetContributor = PythonTokenSetContributor()

  override val language: Language = Language.PYTHON
  override val feature: String = "token-completion"

  override fun getFile(): CodeFragment = _codeFragment
                                         ?: throw PsiConverterException("Invoke 'accept' with visitor on PSI first")

  override fun visitPyFile(file: PyFile) {
    _codeFragment = CodeFragment(file.textOffset, file.textLength)
    super.visitPyFile(file)
  }

  override fun visitElement(node: PsiElement) {
    if (tokenSetContributor.keywordTokens.contains(node.elementType)) {
      val token = CodeToken(node.text, node.textOffset, keywordProperties())
      _codeFragment?.addChild(token)
    }
    super.visitElement(node)
  }

  override fun visitPyReferenceExpression(node: PyReferenceExpression) {
    val name = node.nameElement
    if (name != null) {
      val qualifier = node.qualifier?.reference?.resolve()
      val properties = if (qualifier is PyParameter && !qualifier.isSelf) {
        SimpleTokenProperties.create(TypeProperty.PARAMETER_MEMBER, SymbolLocation.PROJECT) {}
      }
      else {
        TokenProperties.UNKNOWN
      }
      val token = CodeToken(name.text, name.startOffset, properties)
      _codeFragment?.addChild(token)
    }
    super.visitPyReferenceExpression(node)
  }

  override fun visitPyKeywordArgument(node: PyKeywordArgument) {
    val keyword = node.keywordNode
    if (keyword != null) {
      val token = CodeToken(keyword.text, keyword.startOffset, keywordProperties())
      _codeFragment?.addChild(token)
    }
    super.visitPyKeywordArgument(node)
  }

  override fun visitComment(comment: PsiComment) = Unit

  private fun keywordProperties() = SimpleTokenProperties.create(TypeProperty.KEYWORD, SymbolLocation.LIBRARY) {}
}