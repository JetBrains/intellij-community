// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.visitor

import com.intellij.cce.core.CodeFragment
import com.intellij.cce.core.CodeLine
import com.intellij.cce.core.CodeToken
import com.intellij.cce.util.CompletionGolfTextUtil.isValuableString
import com.intellij.cce.visitor.exceptions.PsiConverterException
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement


class LineCompletionVisitorHelper {
  private var codeFragment: CodeFragment? = null
  private val lines = mutableListOf<CodeLine>()

  fun getFile(): CodeFragment {
    codeFragment?.let { file ->
      lines.filter { it.getChildren().isNotEmpty() }.forEach { file.addChild(it) }
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
    val text = element.text.take(MAX_PREFIX_LENGTH)
    if (text.isValuableString()) {
      lines.find { it.offset <= element.startOffset && it.offset + it.text.length > element.startOffset }
        ?.takeIf { it.getChildren().all { it.offset != element.startOffset } }
        ?.addChild(CodeToken(text, element.startOffset))
    }
  }
}

private const val MAX_PREFIX_LENGTH: Int = 4
