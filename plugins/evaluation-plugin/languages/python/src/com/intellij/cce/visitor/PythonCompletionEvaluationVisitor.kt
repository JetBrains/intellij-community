package com.intellij.cce.visitor

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import com.jetbrains.python.PythonTokenSetContributor
import com.jetbrains.python.psi.*
import com.intellij.cce.visitor.exceptions.PsiConverterException
import com.intellij.cce.core.Language
import com.intellij.cce.core.CodeFragment
import com.intellij.cce.core.CodeToken

class PythonCompletionEvaluationVisitor : CompletionEvaluationVisitor, PyRecursiveElementVisitor() {
  private var _codeFragment: CodeFragment? = null
  private val tokenSetContributor = PythonTokenSetContributor()

  override val language: Language = Language.PYTHON

  override fun getFile(): CodeFragment = _codeFragment
                                         ?: throw PsiConverterException("Invoke 'accept' with visitor on PSI first")

  override fun visitPyFile(file: PyFile) {
    _codeFragment = CodeFragment(file.textOffset, file.textLength)
    super.visitPyFile(file)
  }

  override fun visitElement(node: PsiElement) {
    if (tokenSetContributor.keywordTokens.contains(node.elementType)) {
      val token = CodeToken(node.text, node.textOffset, node.textLength)
      _codeFragment?.addChild(token)
    }
    super.visitElement(node)
  }

  override fun visitPyReferenceExpression(node: PyReferenceExpression) {
    val name = node.nameElement
    if (name != null) {
      val token = CodeToken(name.text, name.startOffset, name.textLength)
      _codeFragment?.addChild(token)
    }
    super.visitPyReferenceExpression(node)
  }

  override fun visitPyKeywordArgument(node: PyKeywordArgument) {
    val keyword = node.keywordNode
    if (keyword != null) {
      val token = CodeToken(keyword.text, keyword.startOffset, keyword.textLength)
      _codeFragment?.addChild(token)
    }
    super.visitPyKeywordArgument(node)
  }

  override fun visitComment(comment: PsiComment) = Unit
}