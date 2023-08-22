// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.postfix

import com.intellij.codeInsight.template.postfix.templates.StringBasedPostfixTemplate
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.name.FqNameUnsafe
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

internal class KotlinSpreadPostfixTemplate : StringBasedPostfixTemplate {
    @Suppress("ConvertSecondaryConstructorToPrimary")
    constructor(provider: KotlinPostfixTemplateProvider) : super(
        /* name = */ "spread",
        /* example = */ "*expr",
        /* selector = */ allExpressions(ValuedFilter, ValueParameterFilter, ExpressionTypeFilter { it.canSpread() }),
        /* provider = */ provider
    )

    override fun getTemplateString(element: PsiElement) = "*\$expr$\$END$"
    override fun getElementToRemove(expr: PsiElement) = expr
}

private object ValueParameterFilter : (KtExpression) -> Boolean {
    override fun invoke(expression: KtExpression): Boolean {
        val valueArgument = expression.getParentOfType<KtValueArgument>(strict = true) ?: return false
        return !valueArgument.isSpread
    }
}

private val ARRAY_CLASS_FQ_NAMES: Set<FqNameUnsafe> = buildSet {
    addAll(StandardNames.FqNames.arrayClassFqNameToPrimitiveType.keys)
    add(StandardNames.FqNames.array)
    add(StandardNames.FqNames.uByteArrayFqName.toUnsafe())
    add(StandardNames.FqNames.uShortArrayFqName.toUnsafe())
    add(StandardNames.FqNames.uIntArrayFqName.toUnsafe())
    add(StandardNames.FqNames.uLongArrayFqName.toUnsafe())
}

private fun KtType.canSpread(): Boolean {
    return this is KtNonErrorClassType && classId.asSingleFqName().toUnsafe() in ARRAY_CLASS_FQ_NAMES
}