// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.visitor

import com.intellij.cce.core.CodeFragment
import com.intellij.cce.core.CodeLine
import com.intellij.cce.core.CodeToken
import com.intellij.cce.core.Language
import com.intellij.cce.evaluable.golf.CompletionGolfMode
import com.intellij.cce.util.CompletionGolfTextUtil.isValuableString
import com.intellij.cce.visitor.exceptions.PsiConverterException
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.elementType
import com.intellij.refactoring.suggested.endOffset
import com.jetbrains.python.PythonTokenSetContributor
import com.jetbrains.python.psi.*


class PythonCompletionGolfVisitorFactory : CompletionGolfVisitorFactory {
  override val language: Language = Language.PYTHON
  override fun createVisitor(featureName: String, mode: CompletionGolfMode): CompletionGolfEvaluationVisitor {
    when (mode) {
      CompletionGolfMode.ALL -> return AllVisitor(featureName)
      CompletionGolfMode.TOKENS -> return TokensVisitor(featureName)
    }
  }

  class AllVisitor(override val feature: String) : CompletionGolfAllEvaluationVisitor, PyRecursiveElementVisitor() {
    override val language: Language = Language.PYTHON
    override val processor = CompletionGolfAllEvaluationVisitor.Processor()

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
      if (node.isDocString) processor.skipElement(node)
      super.visitPyStringLiteralExpression(node)
    }
  }

  class TokensVisitor(override val feature: String) : CompletionGolfEvaluationVisitor, PyRecursiveElementVisitor() {
    private var codeFragment: CodeFragment? = null
    private val tokenSetContributor = PythonTokenSetContributor()
    private val lines = mutableListOf<CodeLine>()

    override val language: Language = Language.PYTHON

    override fun getFile(): CodeFragment {
      codeFragment?.let { file ->
        lines.filter { it.getChildren().isNotEmpty() }.forEach { file.addChild(it) }
        return file
      }
      throw PsiConverterException("Invoke 'accept' with visitor on PSI first")
    }

    override fun visitPyFile(file: PyFile) {
      codeFragment = CodeFragment(file.textOffset, file.textLength)
      lines.clear()
      var offset = 0
      for (line in file.text.lines()) {
        lines.add(CodeLine(line, offset))
        offset += line.length + 1
      }
      super.visitFile(file)
    }

    override fun visitElement(element: PsiElement) {
      if (tokenSetContributor.keywordTokens.contains(element.elementType)) {
        addElement(element.node)
      }
      else if (element is PsiNameIdentifierOwner) {
        element.nameIdentifier?.node?.let { addElement(it) }
      }
      super.visitElement(element)
    }

    override fun visitPyReferenceExpression(node: PyReferenceExpression) {
      node.nameElement?.let { addElement(it) }
      super.visitPyReferenceExpression(node)
    }

    override fun visitPyKeywordArgument(node: PyKeywordArgument) {
      node.keywordNode?.let { addElement(it) }
      super.visitPyKeywordArgument(node)
    }

    override fun visitPyStringLiteralExpression(node: PyStringLiteralExpression) {
      if (node.parent is PySubscriptionExpression) {
        addElement(node.node)
      }
      else {
        super.visitPyStringLiteralExpression(node)
      }
    }

    private fun addElement(element: ASTNode) {
      val text = element.text.take(MAX_PREFIX_LENGTH)
      if (text.isValuableString()) {
        lines.find { it.offset <= element.startOffset && it.offset + it.text.length > element.startOffset }?.addChild(
          CodeToken(text, element.startOffset)
        )
      }
    }

    companion object {
      const val MAX_PREFIX_LENGTH: Int = 4
    }
  }
}
