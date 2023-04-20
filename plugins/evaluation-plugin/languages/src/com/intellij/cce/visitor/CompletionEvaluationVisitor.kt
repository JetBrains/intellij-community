package com.intellij.cce.visitor

import com.intellij.cce.core.*
import com.intellij.cce.visitor.exceptions.PsiConverterException
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.refactoring.suggested.endOffset

interface CompletionEvaluationVisitor {
  companion object {
    val EP_NAME: ExtensionPointName<CompletionEvaluationVisitor> = ExtensionPointName.create("com.intellij.cce.completionEvaluationVisitor")
  }

  val language: Language
  fun getFile(): CodeFragment
}

interface CompletionGolfEvaluationVisitor : CompletionEvaluationVisitor {
  val processor: Processor

  override fun getFile() = processor.safeCodeFragment

  class Processor {
    private var codeFragment: CodeFragment? = null
    val safeCodeFragment: CodeFragment
      get() = codeFragment ?: throw PsiConverterException("Invoke 'accept' with visitor on PSI first")

    private val prop = SimpleTokenProperties.create(TypeProperty.LINE, SymbolLocation.UNKNOWN) {}

    private var lastOffset = 0

    fun visitFile(file: PsiFile) {
      lastOffset = 0
      codeFragment = CodeFragment(file.textOffset, file.textLength)
    }

    fun skipElement(element: PsiElement) {
      lastOffset = element.endOffset
    }

    fun handleMultilineElement(element: PsiElement) {
      val newLine = element.textContains('\n')
      if (!newLine) {
        return
      }

      val range = TextRange(lastOffset, element.endOffset)
      lastOffset = element.endOffset

      val text = range.substring(element.containingFile.text)

      var start = range.startOffset
      text.lines().map {
        val t = TextRange(start, start + it.length).substring(element.containingFile.text)
        CodeToken(t, start, t.length, prop).also {
          start += t.length + 1
        }
      }.filter { it.text.isValuableString() }
        .forEach { safeCodeFragment.addChild(it) }
    }

    fun handleLastElement(element: PsiElement) {
      val range = TextRange(lastOffset, element.endOffset)
      val text = range.substring(element.containingFile.text)

      if (text.isValuableString()) {
        safeCodeFragment.addChild(CodeToken(text, range.startOffset, text.length, prop))
      }
    }

    private fun String.isValuableString(): Boolean {
      return find { it.isLetterOrDigit() || it == '\'' || it == '"' } != null
    }
  }

  class Default(override val language: Language = Language.ANOTHER) : CompletionGolfEvaluationVisitor, PsiRecursiveElementVisitor() {
    override val processor = Processor()
    override fun visitComment(comment: PsiComment) {
      processor.skipElement(comment)
      super.visitComment(comment)
    }

    override fun visitFile(file: PsiFile) {
      processor.visitFile(file)
      super.visitFile(file)
    }

    override fun visitWhiteSpace(space: PsiWhiteSpace) {
      processor.handleMultilineElement(space)
      super.visitWhiteSpace(space)
    }

    override fun visitElement(element: PsiElement) {
      if (element.endOffset == element.containingFile.endOffset && element.children.isEmpty()) {
        processor.handleLastElement(element)
      }
      super.visitElement(element)
    }
  }

  companion object {
    val EP_NAME: ExtensionPointName<CompletionGolfEvaluationVisitor> = ExtensionPointName.create(
      "com.intellij.cce.completionGolfEvaluationVisitor"
    )
  }
}
