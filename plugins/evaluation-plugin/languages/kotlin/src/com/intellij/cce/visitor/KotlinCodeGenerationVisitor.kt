// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.visitor

import com.intellij.cce.core.*
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.elementType
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import com.intellij.psi.JavaTokenType
import com.intellij.psi.util.startOffset as psiTreeUtilStartOffset


class KotlinCodeGenerationVisitor : CodeGenerationVisitorBase(Language.KOTLIN) {
  override fun createPsiVisitor(codeFragment: CodeFragment): PsiElementVisitor {
    return KotlinCodeGenerationPsiVisitor(codeFragment)
  }
}

class KotlinCodeGenerationPsiVisitor(private val codeFragment: CodeFragment): KtTreeVisitorVoid() {
  override fun visitNamedFunction(function: KtNamedFunction) {
    codeFragment?.addChild(
      CodeToken(function.text, function.psiTreeUtilStartOffset, SimpleTokenProperties.create(TypeProperty.METHOD, SymbolLocation.PROJECT) {})
    )
    val body = function.bodyExpression?.children?.toList()
    if (body != null) {
      val meaningfulBodyChildren = body.trim()
      if (meaningfulBodyChildren.any()) {
        val firstMeaningfulChildren = meaningfulBodyChildren.first()
        val meaningfulBodyChildrenText = meaningfulBodyChildren.map { it.text }.joinToString("")

        codeFragment?.addChild(
          CodeToken(meaningfulBodyChildrenText, firstMeaningfulChildren.psiTreeUtilStartOffset, SimpleTokenProperties.create(TypeProperty.METHOD_BODY, SymbolLocation.PROJECT) {})
        )
      }
    }
  }}


private fun List<PsiElement>.trim(): List<PsiElement> {
  val firstIndex = this.indexOfFirst { it.isMeaningful()}
  val lastIndex = this.indexOfLast { it.isMeaningful() }
  val indexRange = (firstIndex.. lastIndex)
  return this.filterIndexed { index, it ->
    index in indexRange
  }
}

private fun PsiElement.isMeaningful(): Boolean {
  if (this is PsiWhiteSpace) {
    return false
  }
  val elType = elementType
  if (elType == JavaTokenType.LBRACE || elType == JavaTokenType.RBRACE) {
    return false
  }
  return true
}