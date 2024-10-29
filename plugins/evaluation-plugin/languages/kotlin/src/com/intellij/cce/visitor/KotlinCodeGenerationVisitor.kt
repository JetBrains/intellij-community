// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.visitor

import com.intellij.cce.core.*
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.startOffset
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.getChildrenOfType

class KotlinCodeGenerationVisitor : CodeGenerationVisitorBase(Language.KOTLIN) {
  override fun createPsiVisitor(codeFragment: CodeFragment): PsiElementVisitor {
    return KotlinCodeGenerationPsiVisitor(codeFragment)
  }
}

class KotlinCodeGenerationPsiVisitor(private val codeFragment: CodeFragment) : KtTreeVisitorVoid() {
  override fun visitNamedFunction(function: KtNamedFunction) {
    codeFragment.addChild(
      CodeToken(function.text, function.startOffset, SimpleTokenProperties.create(TypeProperty.METHOD, SymbolLocation.PROJECT) {})
    )

    val body = function.bodyExpression?.getChildrenOfType<PsiElement>()?.toList()
    if (body != null) {
      extractMeaningfulContent(body, setOf(KtTokens.LBRACE, KtTokens.RBRACE))?.let { result ->
        codeFragment.addChild(result)
      }
    }
  }
}
