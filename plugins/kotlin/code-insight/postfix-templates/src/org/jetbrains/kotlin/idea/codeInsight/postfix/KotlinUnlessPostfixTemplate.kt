// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.postfix

import com.intellij.codeInsight.template.postfix.templates.StringBasedPostfixTemplate
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.types.classId
import org.jetbrains.kotlin.analysis.api.types.isMarkedNullable
import org.jetbrains.kotlin.analysis.api.types.KaStandardTypeClassIds

internal class KotlinUnlessPostfixTemplate : StringBasedPostfixTemplate {
    @Suppress("ConvertSecondaryConstructorToPrimary")
    constructor(provider: KotlinPostfixTemplateProvider) : super(
        /* name = */ "unless",
        /* example = */ "if (!expr) {}",
        /* selector = */ allExpressions(ValuedFilter, StatementFilter, ExpressionTypeFilter { it.classId == KaStandardTypeClassIds.BOOLEAN && !it.isMarkedNullable }),
        /* provider = */ provider
    )

    override fun getTemplateString(element: PsiElement): String {
        val negatedExpression = KotlinPostfixTemplatePsiInfo.getNegatedExpression(element)
        return "if (${negatedExpression.text}) {\n\$END$\n}"
    }

    override fun getElementToRemove(expr: PsiElement): PsiElement = expr
    override fun isApplicableForModCommand(): Boolean = true
}