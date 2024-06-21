// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.visitor

import com.intellij.cce.core.*
import com.intellij.cce.visitor.exceptions.PsiConverterException
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset

class KotlinCodeGenerationVisitor : EvaluationVisitor, KtTreeVisitorVoid() {
  private var codeFragment: CodeFragment? = null

  override val language: Language = Language.KOTLIN
  override val feature: String = "code-generation"
  override fun getFile(): CodeFragment = codeFragment
                                         ?: throw PsiConverterException("Invoke 'accept' with visitor on PSI first")

  override fun visitNamedFunction(function: KtNamedFunction) {
    codeFragment?.addChild(
      CodeToken(function.text, function.startOffset, SimpleTokenProperties.create(TypeProperty.METHOD, SymbolLocation.PROJECT) {})
    )
    val body = function.bodyExpression
    if (body != null) {
      val meaningfulBodyChildren = (body as? KtBlockExpression)?.trim() ?: listOf(body)
      if (meaningfulBodyChildren.isNotEmpty()) {
        val firstMeaningfulChild = meaningfulBodyChildren.first()
        val meaningfulBodyText = meaningfulBodyChildren.joinToString("") { it.text }

        codeFragment?.addChild(
          CodeToken(meaningfulBodyText, firstMeaningfulChild.startOffset, SimpleTokenProperties.create(TypeProperty.METHOD_BODY, SymbolLocation.PROJECT) {})
        )
      }
    }
  }

  private fun KtBlockExpression.trim(): List<KtElement> {
    val firstIndex = children.indexOfFirst { it is KtExpression }
    val lastIndex = children.indexOfLast { it is KtExpression }
    val indexRange = firstIndex..lastIndex
    return children.filterIndexed { index, _ -> index in indexRange }.filterIsInstance<KtElement>()
  }
}


private val BODY = SimpleTokenProperties.create(TypeProperty.UNKNOWN, SymbolLocation.UNKNOWN) {}
