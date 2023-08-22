// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.liveTemplates.macro

import com.intellij.codeInsight.template.Expression
import com.intellij.codeInsight.template.ExpressionContext
import com.intellij.codeInsight.template.Result
import com.intellij.codeInsight.template.TextResult
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

class KotlinFunctionNameMacro : KotlinMacro() {
    override fun getName() = "kotlinFunctionName"
    override fun getPresentableName() = "kotlinFunctionName()"

    override fun calculateResult(params: Array<Expression>, context: ExpressionContext): Result? {
        val element = context.psiElementAtStartOffset?.getNonStrictParentOfType<KtNamedFunction>()
        val name = element?.name ?: return null
        return TextResult(name)
    }
}