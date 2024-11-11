package com.intellij.cce.python.visitor

import com.intellij.cce.core.*
import com.intellij.cce.visitor.EvaluationVisitor
import com.intellij.cce.visitor.exceptions.PsiConverterException
import com.intellij.psi.util.endOffset
import com.intellij.psi.util.startOffset
import com.jetbrains.python.psi.*

class PythonDocGenerationVisitor : EvaluationVisitor, PyRecursiveElementVisitor() {
  override val feature: String = "doc-generation"
  private var codeFragment: CodeFragment? = null

  override val language: Language = Language.PYTHON

  override fun getFile(): CodeFragment = codeFragment
                                         ?: throw PsiConverterException("Invoke 'accept' with visitor on PSI first")

  override fun visitPyFile(file: PyFile) {
    codeFragment = CodeFragment(file.textOffset, file.textLength).apply { text = file.text }
    super.visitPyFile(file)
  }

  override fun visitPyFunction(function: PyFunction) {
    val docComment = function.docStringExpression
    val nameIdentifier = function.nameIdentifier
    if (docComment != null && nameIdentifier != null) {
      codeFragment?.addChild(
        CodeToken(
          function.text,
          function.textRange.startOffset,
          DocumentationProperties(docComment.text, function.startOffset, function.endOffset, docComment.startOffset, docComment.endOffset, nameIdentifier.startOffset)
        )
      )
    }
    super.visitPyFunction(function)
  }

  override fun visitPyClass(klass: PyClass) {
    val docComment = klass.docStringExpression
    val nameIdentifier = klass.nameIdentifier
    if (docComment != null && nameIdentifier != null) {
      codeFragment?.addChild(
        CodeToken(
          klass.text,
          klass.textRange.startOffset,
          DocumentationProperties(docComment.text, klass.startOffset, klass.endOffset, docComment.startOffset, docComment.endOffset, nameIdentifier.startOffset)
        )
      )
    }
    super.visitPyClass(klass)
  }
}