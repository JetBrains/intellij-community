package com.intellij.cce.visitor

import com.intellij.cce.core.Language
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.refactoring.suggested.endOffset
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyRecursiveElementVisitor
import com.jetbrains.python.psi.PyStringLiteralExpression

open class PythonCompletionGolfEvaluationVisitor : CompletionGolfEvaluationVisitor, PyRecursiveElementVisitor() {
  override val language: Language = Language.PYTHON
  override val processor = CompletionGolfEvaluationVisitor.Processor()

  override fun visitComment(comment: PsiComment) {
    processor.skipElement(comment)
    super.visitComment(comment)
  }

  override fun visitPyFile(file: PyFile) {
    processor.visitFile(file)
    super.visitFile(file)
  }

  override fun visitElement(element: PsiElement) {
    if (element.endOffset == element.containingFile.endOffset && element.children.isEmpty()) {
      processor.handleLastElement(element)
    }
    super.visitElement(element)
  }

  override fun visitWhiteSpace(space: PsiWhiteSpace) {
    processor.handleMultilineElement(space)
    super.visitWhiteSpace(space)
  }

  override fun visitPyStringLiteralExpression(node: PyStringLiteralExpression) {
    if (node.isDocString || node.stringValueTextRange.length > MAX_STRING_LENGTH) processor.skipElement(node)
    super.visitPyStringLiteralExpression(node)
  }

  companion object {
    private const val MAX_STRING_LENGTH: Int = 20
  }
}
