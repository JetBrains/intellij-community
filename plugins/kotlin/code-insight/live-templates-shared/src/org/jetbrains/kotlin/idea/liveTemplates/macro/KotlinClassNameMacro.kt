// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.liveTemplates.macro

import com.intellij.codeInsight.template.Expression
import com.intellij.codeInsight.template.ExpressionContext
import com.intellij.codeInsight.template.Result
import com.intellij.codeInsight.template.TextResult
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf

class KotlinClassNameMacro : KotlinMacro() {
    override fun getName() = "kotlinClassName"
    override fun getPresentableName() = "kotlinClassName()"

    override fun calculateResult(params: Array<Expression>, context: ExpressionContext): Result? {
        val targetElement = context.psiElementAtStartOffset ?: return null

        val name = targetElement
            .parentsWithSelf
            .filterIsInstance<KtClassOrObject>()
            .filter { !it.hasModifier(KtTokens.COMPANION_KEYWORD) }
            .firstNotNullOfOrNull { it.name }
            ?: return null

        return TextResult(name)
    }
}