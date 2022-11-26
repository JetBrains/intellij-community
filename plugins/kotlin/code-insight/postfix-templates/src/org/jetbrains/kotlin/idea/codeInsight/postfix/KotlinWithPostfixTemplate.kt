// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.postfix

import com.intellij.codeInsight.template.postfix.templates.StringBasedPostfixTemplate
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.psi.*

internal class KotlinWithPostfixTemplate : StringBasedPostfixTemplate {
    @Suppress("ConvertSecondaryConstructorToPrimary")
    constructor(provider: KotlinPostfixTemplateProvider) : super(
        /* name = */ "with",
        /* example = */ "with(expr) {}",
        /* selector = */ allExpressions(ValuedFilter, StatementFilter, WhenTargetFilter, ExpressionTypeFilter { isWithTargetType(it) }),
        /* provider = */ provider
    )

    override fun getTemplateString(element: PsiElement) = "kotlin.with(\$expr$) {\n\$END$\n}"
    override fun getElementToRemove(expr: PsiElement) = expr
}

private object WhenTargetFilter : (KtExpression) -> Boolean {
    override fun invoke(expression: KtExpression): Boolean {
        return when (KtPsiUtil.deparenthesize(expression)) {
            is KtConstantExpression -> false
            is KtStringTemplateExpression -> false
            is KtDeclaration -> false
            is KtLambdaExpression -> false
            is KtIfExpression -> false
            is KtWhileExpression -> false
            is KtTryExpression -> false
            is KtClassLiteralExpression -> false
            is KtObjectLiteralExpression -> false
            is KtLabeledExpression -> false
            else -> true
        }
    }
}

private fun KtAnalysisSession.isWithTargetType(type: KtType): Boolean {
    return !type.isUnit && !type.isMarkedNullable && !type.isPrimitive
}