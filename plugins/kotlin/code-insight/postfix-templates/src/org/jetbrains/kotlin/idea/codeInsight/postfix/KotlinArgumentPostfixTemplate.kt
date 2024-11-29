// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.postfix

import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.postfix.templates.StringBasedPostfixTemplate
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtThrowExpression

internal class KotlinArgumentPostfixTemplate : StringBasedPostfixTemplate, DumbAware {
    @Suppress("ConvertSecondaryConstructorToPrimary")
    constructor(provider: KotlinPostfixTemplateProvider) : super(
        /* name = */ "arg",
        /* example = */ "functionCall(expr)",
        /* selector = */ allExpressions(ValuedFilter, StatementFilter, ExpressionTypeFilter { it !is KtReturnExpression && it !is KtThrowExpression }),
        /* provider = */ provider
    )

    override fun getTemplateString(element: PsiElement): String = "\$call$(\$expr$\$END$)"
    override fun getElementToRemove(expr: PsiElement): PsiElement = expr
    override fun setVariables(template: Template, element: PsiElement) {
        template.addVariable("call", "", "", true)
    }
}