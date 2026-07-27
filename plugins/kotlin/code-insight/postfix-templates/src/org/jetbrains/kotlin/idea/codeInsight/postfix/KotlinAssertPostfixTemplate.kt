// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.postfix

import com.intellij.codeInsight.template.postfix.templates.StringBasedPostfixTemplate
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.types.isBooleanType
import org.jetbrains.kotlin.analysis.api.types.isMarkedNullable

internal class KotlinAssertPostfixTemplate : StringBasedPostfixTemplate {
    @Suppress("ConvertSecondaryConstructorToPrimary")
    constructor(provider: KotlinPostfixTemplateProvider) : super(
        /* name = */ "assert",
        /* example = */ "assert(expr)",
        /* selector = */ allExpressions(ValuedFilter, StatementFilter, ExpressionTypeFilter { it.isBooleanType && !it.isMarkedNullable }),
        /* provider = */ provider
    )

    override fun getTemplateString(element: PsiElement): String = "kotlin.assert(\$expr$)\$END$"
    override fun getElementToRemove(expr: PsiElement): PsiElement = expr
    override fun isApplicableForModCommand(): Boolean = true
}