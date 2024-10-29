package com.intellij.cce.visitor

import com.intellij.cce.core.*
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile

// TODO should be language-agnostic
abstract class SelfIdentificationVisitor : EvaluationVisitor, PsiElementVisitor() {
  override val feature: String = "self-identification"

  private lateinit var codeFragment: CodeFragment

  override fun getFile(): CodeFragment = codeFragment

  override fun visitFile(file: PsiFile) {
    codeFragment = CodeFragment(file.textOffset, file.textLength).apply {
      spacedLines(file.text).forEach {
        addChild(it)
      }
    }
    super.visitFile(file)
  }

  private fun spacedLines(text: String): List<CodeToken> {
    val lines = text.lines()
    val result = mutableListOf<CodeToken>()

    var offset = 0
    var previousWasAdded = false
    for (lineNumber in lines.indices) {
      val line = lines[lineNumber]
      val currentOffset = offset
      offset += line.length + 1

      if (previousWasAdded) {
        previousWasAdded = false
        continue
      }

      if (line.trim().length < 10) {
        continue
      }

      result.add(CodeToken(line, currentOffset))
      previousWasAdded = true
    }

    return result
  }
}
