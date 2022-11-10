package org.jetbrains.completion.full.line.js.formatters

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.completion.full.line.language.ElementFormatter

class LeadingSpaceFormatter : ElementFormatter {
  /**
  * Removes leading spaces and empty lines.
  */
  override fun condition(element: PsiElement): Boolean = element is PsiWhiteSpace

  override fun filter(element: PsiElement): Boolean? = null

  override fun format(element: PsiElement): String {
    element is PsiWhiteSpace

    val text = element.text
    // JSDocComment might be the first child of the next sibling
    return if ("\n" in text) (if (containsCommentAtFirst(element.nextSibling)) "" else "\n") else text
  }

  private fun containsCommentAtFirst(element: PsiElement?): Boolean = element != null && (element is PsiComment || containsCommentAtFirst(element.firstChild))
}
