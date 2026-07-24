// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.postfix

import com.intellij.codeInsight.template.postfix.templates.NotPostfixTemplate
import org.jetbrains.kotlin.analysis.api.types.isBooleanType
import org.jetbrains.kotlin.analysis.api.types.isMarkedNullable
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPrefixExpression

internal class KotlinNotPostfixTemplate : NotPostfixTemplate {
    @Suppress("ConvertSecondaryConstructorToPrimary")
    constructor(provider: KotlinPostfixTemplateProvider) : super(
        /* info = */ KotlinPostfixTemplatePsiInfo,
        /* selector = */ allExpressions(ValuedFilter, NotExpressionFilter, ExpressionTypeFilter { it.isBooleanType && !it.isMarkedNullable }),
        /* provider = */ provider
    )

    override fun isApplicableForModCommand(): Boolean = true
}

private object NotExpressionFilter : (KtExpression) -> Boolean {
    override fun invoke(expression: KtExpression): Boolean {
        val parent = expression.parent
        // Avoid double negation ('!foo' -> '!!foo')
        return !(parent is KtPrefixExpression && parent.operationToken == KtTokens.EXCL)
    }
}