package org.jetbrains.completion.full.line.language.formatters

import com.intellij.psi.PsiElement
import org.jetbrains.completion.full.line.language.ElementFormatter

class SkippedElementsFormatter(private vararg val elementsToSkip: Class<out PsiElement>) : ElementFormatter {
  override fun condition(element: PsiElement): Boolean = elementsToSkip.any { it.isAssignableFrom(element::class.java) }

  override fun filter(element: PsiElement): Boolean = condition(element)

  override fun format(element: PsiElement): String = ""
}
