package com.intellij.cce.visitor

import com.intellij.cce.core.CodeToken
import com.intellij.cce.core.SimpleTokenProperties
import com.intellij.cce.core.SymbolLocation
import com.intellij.cce.core.TypeProperty
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.elementType
import com.intellij.psi.util.startOffset

fun extractMeaningfulContent(block: List<PsiElement>, tokensToSkip: Set<IElementType>): CodeToken? {
  val meaningfulChildren = block.trim(tokensToSkip)
  if (meaningfulChildren.any()) {
    val firstMeaningfulChildren = meaningfulChildren.first()
    val meaningfulText = meaningfulChildren.joinToString("") { it.text }
    return CodeToken(meaningfulText, firstMeaningfulChildren.startOffset, SimpleTokenProperties.create(TypeProperty.METHOD_BODY, SymbolLocation.PROJECT) {})
  }
  return null
}

private fun List<PsiElement>.trim(tokensToSkip: Set<IElementType>): List<PsiElement> {
  val firstIndex = this.indexOfFirst { it.isMeaningful(tokensToSkip) }
  val lastIndex = this.indexOfLast { it.isMeaningful(tokensToSkip) }
  val indexRange = (firstIndex..lastIndex)
  return this.filterIndexed { index, it ->
    index in indexRange
  }
}

private fun PsiElement.isMeaningful(tokensToSkip: Set<IElementType>): Boolean {
  if (this is PsiWhiteSpace) {
    return false
  }
  if (elementType in tokensToSkip) {
    return false
  }
  return true
}
