package com.intellij.cce.visitor

import com.intellij.cce.core.*
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import com.jetbrains.python.PythonTokenSetContributor
import com.jetbrains.python.psi.*
import com.intellij.cce.visitor.exceptions.PsiConverterException

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
      } else {
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