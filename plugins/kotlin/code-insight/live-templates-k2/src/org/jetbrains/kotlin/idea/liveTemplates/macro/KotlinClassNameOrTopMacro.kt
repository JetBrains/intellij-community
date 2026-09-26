// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.liveTemplates.macro

import com.intellij.codeInsight.template.Expression
import com.intellij.codeInsight.template.ExpressionContext
import com.intellij.codeInsight.template.Result
import com.intellij.codeInsight.template.TextResult
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf

class KotlinClassNameOrTopMacro : KotlinMacro() {
    override fun getName(): String = "kotlinClassNameOrTop"
    override fun getPresentableName(): String = "kotlinClassNameOrTop()"

    override fun calculateResult(params: Array<Expression>, context: ExpressionContext): Result? {
        val targetElement = context.psiElementAtStartOffset ?: return null

        val name = targetElement
            .parentsWithSelf
            .filterIsInstance<KtClassOrObject>()
            .filter { !it.hasModifier(KtTokens.COMPANION_KEYWORD) }
            .firstNotNullOfOrNull { it.name }
            ?: return TextResult("top")

        return TextResult(name)
    }
}