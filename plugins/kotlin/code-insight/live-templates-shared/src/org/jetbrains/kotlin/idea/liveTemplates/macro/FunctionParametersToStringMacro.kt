// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.liveTemplates.macro

import com.intellij.codeInsight.template.Expression
import com.intellij.codeInsight.template.ExpressionContext
import com.intellij.codeInsight.template.Result
import com.intellij.codeInsight.template.TextResult
import org.jetbrains.kotlin.psi.KtFunction

class FunctionParametersToStringMacro : KotlinMacro() {
    override fun getName(): String = "functionParametersToString"
    override fun getPresentableName(): String = "functionParametersToString()"

    override fun calculateResult(params: Array<Expression>, context: ExpressionContext): Result? {
        var targetElement = context.psiElementAtStartOffset
        while (targetElement != null) {
            if (targetElement is KtFunction) {
                return TextResult("\"" + targetElement.valueParameters.mapNotNull { it.name }.joinToString { "$it = [\${$it}]" } + "\"")
            }
            targetElement = targetElement.parent
        }
        return null
    }
}
