// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.postfix

import com.intellij.codeInsight.template.postfix.templates.NotPostfixTemplate
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPrefixExpression

internal class KotlinNotPostfixTemplate : NotPostfixTemplate {
    @Suppress("ConvertSecondaryConstructorToPrimary")
    constructor(provider: KotlinPostfixTemplateProvider) : super(
        /* info = */ KotlinPostfixTemplatePsiInfo,
        /* selector = */ allExpressions(ValuedFilter, NotExpressionFilter, ExpressionTypeFilter { it.isBoolean && !it.isMarkedNullable }),
        /* provider = */ provider
    )
}

private object NotExpressionFilter : (KtExpression) -> Boolean {
    override fun invoke(expression: KtExpression): Boolean {
        val parent = expression.parent
        if (parent is KtPrefixExpression && parent.operationToken == KtTokens.EXCL) {
            // Avoid double negation ('!foo' -> '!!foo')
            return false
        }

        return true
    }
}