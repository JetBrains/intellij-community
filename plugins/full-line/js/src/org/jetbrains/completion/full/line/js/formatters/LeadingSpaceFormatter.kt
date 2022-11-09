package org.jetbrains.completion.full.line.js.formatters

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
    val text = element.text
    return if ("\n" in text) "\n" else text
  }
}
