package com.intellij.cce.visitor

import com.intellij.cce.core.*
import com.intellij.openapi.project.Project
import com.intellij.cce.visitor.exceptions.PsiConverterException
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset

class CompletionGolfFragmentBuilder(project: Project, language: Language) : CodeFragmentFromPsiBuilder(project, language) {
  override fun getVisitors(): List<CompletionEvaluationVisitor> = listOf(CompletionGolfVisitor(language))

  class CompletionGolfVisitor(override val language: Language) : CompletionEvaluationVisitor, PsiRecursiveElementVisitor() {
    private var codeFragment: CodeFragment? = null
    private val prop = SimpleTokenProperties.create(TypeProperty.LINE, SymbolLocation.UNKNOWN) {}

    private var lastOffset = 0

    override fun getFile() = codeFragment ?: throw PsiConverterException("Invoke 'accept' with visitor on PSI first")

    override fun visitFile(file: PsiFile) {
      lastOffset = 0
      codeFragment = CodeFragment(file.textOffset, file.textLength)
      super.visitFile(file)
    }

    override fun visitComment(comment: PsiComment) = Unit

    override fun visitWhiteSpace(space: PsiWhiteSpace) {
      val newLine = space.textContains('\n')
      if (!newLine) {
        super.visitWhiteSpace(space)
        return
      }

      val range = TextRange(lastOffset, space.startOffset)
      lastOffset = space.endOffset

      val firstComment = SyntaxTraverser.psiTraverser()
        .withRoot(space.containingFile)
        .onRange(range)
        .filter { it is PsiComment }
        .firstOrNull()
      val commentStart = firstComment?.startOffset

      if (commentStart == range.startOffset) {
        super.visitWhiteSpace(space)
        return
      }

      val text = TextRange(range.startOffset, commentStart ?: range.endOffset).substring(space.containingFile.text)

      // Take only valuable lines
      if (text.contains("\n")) {
        var start = range.startOffset
        text.lines().map {
          val t = TextRange(start, start + it.length).substring(space.containingFile.text)
          CodeToken(t, start, t.length, prop).also {
            start += t.length + 1
          }
        }.filter { it.text.find { it.isLetterOrDigit() || it == '\'' || it == '"' } != null }
          .forEach { codeFragment?.addChild(it) }
      } else {
        // Take only valuable lines
        if (text.find { it.isLetterOrDigit() || it == '\'' || it == '"' } != null) {
          codeFragment?.addChild(CodeToken(text, range.startOffset, text.length, prop))
        }
      }
      super.visitWhiteSpace(space)
    }
  }

}
