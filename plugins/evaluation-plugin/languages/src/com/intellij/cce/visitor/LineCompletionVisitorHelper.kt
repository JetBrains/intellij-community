// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.visitor

import com.intellij.cce.core.*
import com.intellij.cce.util.CompletionGolfTextUtil.isValuableString
import com.intellij.cce.visitor.exceptions.PsiConverterException
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement


class LineCompletionVisitorHelper(val maxPrefixLength: Int = 4) {
  private var codeFragment: CodeFragment? = null
  private val lines = mutableListOf<CodeLine>()

  fun getFile(): CodeFragment {
    codeFragment?.let { file ->
      lines.filter { it.getChildren().isNotEmpty() }.forEach { file.addChild(it) }
      file.validateCorrectness()
      return file
    }
    throw PsiConverterException("Invoke 'accept' with visitor on PSI first")
  }

  fun visitFile(file: PsiElement) {
    codeFragment = CodeFragment(file.textOffset, file.textLength)
    lines.clear()
    var offset = 0
    for (line in file.text.lines()) {
      lines.add(CodeLine(line, offset))
      offset += line.length + 1
    }
  }

  fun addElement(element: ASTNode) {
    val text = element.text.take(maxPrefixLength)
    if (text.isValuableString()) {
      lines.find { it.offset <= element.startOffset && it.offset + it.text.length > element.startOffset }
        ?.takeIf { it.getChildren().all { it.offset != element.startOffset } }
        ?.addChild(CodeToken(text, element.startOffset))
    }
  }

  fun addElement(element: ASTNode, psiElement: PsiElement) {
    val text = element.text.take(maxPrefixLength)
    if (text.isValuableString()) {
      lines.find { it.offset <= element.startOffset && it.offset + it.text.length > element.startOffset }
        ?.takeIf { it.getChildren().all { it.offset != element.startOffset } }
        ?.addChild(CodeTokenWithPsi(text, element.startOffset, TokenProperties.UNKNOWN, psiElement))
    }
  }

  private fun CodeFragment.validateCorrectness() {
    var lastEndOffset = 0
    for (line in getChildren()) {
      assert(line is CodeLine) { "Code fragment should only contain code lines" }
      val tokens = (line as CodeLine).getChildren()
      for (token in tokens) {
        assert(lastEndOffset <= token.offset) { "Code tokens shouldn't overlap" }
        lastEndOffset = token.offset + token.text.length
      }
    }
  }
}