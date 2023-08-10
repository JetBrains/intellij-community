// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.SyntaxTraverser
import com.intellij.refactoring.suggested.endOffset

/**
 * This class is needed to compute effective range without trailing right braces and whitespaces.
 *
 * @see VcsCodeVisionLanguageContext.computeEffectiveRange
 */
abstract class VcsCodeVisionCurlyBracketLanguageContext : VcsCodeVisionLanguageContext {

  protected abstract fun isRBrace(element: PsiElement): Boolean

  override fun computeEffectiveRange(element: PsiElement): TextRange {
    val startOffset = super.computeEffectiveRange(element).startOffset
    return TextRange.create(startOffset, computeEffectiveEndOffset(element))
  }

  private fun computeEffectiveEndOffset(element: PsiElement): Int {
    val end = SyntaxTraverser
                .psiApiReversed()
                .children(element.lastChild)
                .firstOrNull { it !is PsiWhiteSpace && !isRBrace(it) } ?: element
    return end.endOffset
  }
}