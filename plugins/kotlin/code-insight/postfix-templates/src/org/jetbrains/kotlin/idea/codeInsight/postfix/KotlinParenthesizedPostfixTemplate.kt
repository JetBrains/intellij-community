// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.postfix

import com.intellij.codeInsight.template.postfix.templates.StringBasedPostfixTemplate
import com.intellij.psi.PsiElement

internal class KotlinParenthesizedPostfixTemplate : StringBasedPostfixTemplate {
    @Suppress("ConvertSecondaryConstructorToPrimary")
    constructor(provider: KotlinPostfixTemplateProvider) : super(
        /* name = */ "par",
        /* example = */ "(expr)",
        /* selector = */ allExpressions(ValuedFilter),
        /* provider = */ provider
    )

    override fun getTemplateString(element: PsiElement) = "(\$expr$)\$END$"
    override fun getElementToRemove(expr: PsiElement) = expr
}