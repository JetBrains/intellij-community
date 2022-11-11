// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.postfix

import com.intellij.codeInsight.template.postfix.templates.StringBasedPostfixTemplate
import com.intellij.psi.PsiElement

internal class KotlinNotNullPostfixTemplate(provider: KotlinPostfixTemplateProvider)
    : AbstractKotlinNotNullPostfixTemplate("notnull", provider)

internal class KotlinNnPostfixTemplate(provider: KotlinPostfixTemplateProvider)
    : AbstractKotlinNotNullPostfixTemplate("nn", provider)

internal abstract class AbstractKotlinNotNullPostfixTemplate : StringBasedPostfixTemplate {
    @Suppress("ConvertSecondaryConstructorToPrimary")
    constructor(name: String, provider: KotlinPostfixTemplateProvider) : super(
        /* name = */ name,
        /* example = */ "if (expr != null) {}",
        /* selector = */ allExpressions(ValuedFilter, StatementFilter, ExpressionTypeFilter { it.isMarkedNullable }),
        /* provider = */ provider
    )

    override fun getTemplateString(element: PsiElement) = "if (\$expr$ != null) {\n\$END$\n}"
    override fun getElementToRemove(expr: PsiElement) = expr
}