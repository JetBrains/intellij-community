package com.intellij.grazie.text

import ai.grazie.nlp.tokenizer.sentence.SRXSentenceTokenizer
import com.intellij.grazie.text.TextContent.TextDomain.COMMENTS
import com.intellij.grazie.text.TextContent.TextDomain.DOCUMENTATION
import com.intellij.grazie.utils.Text
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.search.PsiTodoSearchHelper

internal class CommentProblemFilter : ProblemFilter() {

  override fun shouldIgnore(problem: TextProblem): Boolean {
    val text = problem.text
    val domain = text.domain
    if (domain == COMMENTS || domain == DOCUMENTATION) {
      if (isTodoComment(text.commonParent.containingFile, text)) {
        return true
      }
      if (problem.rule.globalId.endsWith("DOUBLE_PUNCTUATION") && (isNumberRange(problem, text) || isPathPart(problem, text))) {
        return true
      }
      if (problem.rule.globalId.startsWith("LanguageTool.") && isAboutIdentifierParts(problem, text)) {
        return true
      }
    }

    if (domain == DOCUMENTATION) {
      return isInFirstSentence(problem) && problem.fitsGroup(RuleGroup(RuleGroup.INCOMPLETE_SENTENCE))
    }

    if (domain == COMMENTS) {
      if (looksLikeCode(textAround(text, problem.highlightRange))) {
        return true
      }
      if (problem.fitsGroup(RuleGroup(RuleGroup.UNDECORATED_SENTENCE_SEPARATION))) {
        return true
      }
      if (Text.isSingleSentence(text) && problem.fitsGroup(RuleGroup.UNDECORATED_SINGLE_SENTENCE)) {
        return true
      }
    }
    return false
  }

  private fun textAround(text: CharSequence, range: TextRange): CharSequence {
    return text.subSequence((range.startOffset - 20).coerceAtLeast(0), (range.endOffset + 20).coerceAtMost(text.length))
  }

  private fun looksLikeCode(text: CharSequence): Boolean {
    var codeChars = 0
    var textChars = 0
    for (c in text) {
      if ("(){}[]<>=+-*/%|&!;,.:\"'\\@$#^".contains(c)) {
        codeChars++
      } else if (c.isLetterOrDigit()) {
        textChars++
      }
    }
    return codeChars > 0 && textChars / codeChars < 4
  }

  private fun isInFirstSentence(problem: TextProblem) =
    SRXSentenceTokenizer.tokenize(problem.text.substring(0, problem.highlightRange.startOffset)).size <= 1

  private fun isNumberRange(problem: TextProblem, text: TextContent): Boolean {
    val range = problem.highlightRange
    return range.startOffset > 0 && range.endOffset < text.length &&
           text[range.startOffset - 1].isDigit() && text[range.endOffset].isDigit()
  }

  private fun isPathPart(problem: TextProblem, text: TextContent): Boolean {
    val range = problem.highlightRange
    return text.subSequence(0, range.startOffset).endsWith('/') ||
           text.subSequence(range.endOffset, text.length).startsWith('/')
  }

  private fun isAboutIdentifierParts(problem: TextProblem, text: TextContent): Boolean {
    val range = problem.highlightRange
    return text.subSequence(0, range.startOffset).endsWith('_') ||
           text.subSequence(range.endOffset, text.length).startsWith('_')
  }

  // the _todo_ word spoils the grammar of what follows
  private fun isTodoComment(file: PsiFile, text: TextContent) =
    PsiTodoSearchHelper.SERVICE.getInstance(file.project).findTodoItems(file).any { text.intersectsRange(it.textRange) }

}