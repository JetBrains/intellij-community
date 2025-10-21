// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.postfix

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.caches.resolve.safeAnalyze
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

@ApiStatus.Obsolete
@Deprecated("Use KtExpressionPostfixTemplateSelector")
internal class K1ExpressionPostfixTemplateSelector (
    checkCanBeUsedAsValue: Boolean,
    statementsOnly: Boolean,
    expressionPredicate: ((KtExpression)-> Boolean)?,
    predicate: ((KtExpression, BindingContext) -> Boolean)?
) : AbstractKtExpressionPostfixTemplateSelector<BindingContext>(checkCanBeUsedAsValue, statementsOnly, expressionPredicate, predicate) {

    override fun applyPredicate(
        element: KtExpression,
        predicate: ((KtExpression, BindingContext) -> Boolean)?
    ): Boolean? =
        predicate?.invoke(element, element.safeAnalyze(element.getResolutionFacade(), BodyResolveMode.PARTIAL))
}