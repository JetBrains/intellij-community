package com.intellij.cce.visitor

import com.intellij.cce.core.*
import com.intellij.cce.util.CompletionGolfTextUtil.isValuableString
import com.intellij.cce.visitor.exceptions.PsiConverterException
import com.intellij.psi.*
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import kotlin.math.min


interface CompletionGolfEvaluationVisitor : CompletionEvaluationVisitor


interface CompletionGolfAllEvaluationVisitor : CompletionGolfEvaluationVisitor {
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

      var start = lastOffset
      var offset = 0
      for (line in element.containingFile.text.lines()) {
        if (offset < element.endOffset && element.startOffset <= offset + line.length + 1) {
          val text = element.containingFile.text.substring(start, min(offset + line.length, element.endOffset))
          if (text.isValuableString()) {
            safeCodeFragment.addChild(CodeLine(line, offset).apply { addChild(CodeToken(text, start, prop)) })
          }
          start += text.length + 1
        } else if (offset > element.endOffset) {
          break
        }
        offset += line.length + 1
      }
      lastOffset = element.endOffset
    }

    fun handleLastElement(element: PsiElement) {
      var offset = 0
      for (line in element.containingFile.text.lines()) {
        if (lastOffset >= offset) {
          val text = element.containingFile.text.substring(lastOffset, element.endOffset)
          if (text.isValuableString()) {
            safeCodeFragment.addChild(CodeLine(line, offset).apply { addChild(CodeToken(text, lastOffset, prop)) })
          }
        }
        offset += line.length + 1
      }
    }
  }


  class Default(override val language: Language = Language.ANOTHER) : CompletionGolfAllEvaluationVisitor, PsiRecursiveElementVisitor() {
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
}
