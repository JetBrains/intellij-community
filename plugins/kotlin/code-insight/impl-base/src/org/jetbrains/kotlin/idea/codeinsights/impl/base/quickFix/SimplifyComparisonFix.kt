// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.SimplifyBooleanWithConstantsUtils.areThereExpressionsToBeSimplified
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.SimplifyBooleanWithConstantsUtils.performSimplification
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.SimplifyBooleanWithConstantsUtils.removeRedundantAssertion
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

@ApiStatus.Internal
class SimplifyComparisonFix(
    psiElement: KtExpression,
    compareResult: Boolean,
) : KotlinPsiUpdateModCommandAction.ElementBased<KtExpression, SimplifyComparisonFix.Context>(psiElement, Context(compareResult)) {

    class Context(
        val compareResult: Boolean,
    )

    override fun invoke(
        actionContext: ActionContext,
        element: KtExpression,
        context: Context,
        updater: ModPsiUpdater,
    ) {
        val replacement = KtPsiFactory(element.project).createExpression("${context.compareResult}")
        val result = element.replaced(replacement)

        val booleanExpression = result.getNonStrictParentOfType<KtBinaryExpression>()
        if (booleanExpression != null && areThereExpressionsToBeSimplified(booleanExpression)) {
            performSimplification(booleanExpression)
        } else {
            removeRedundantAssertion(result)
        }

        val ifExpression = result.getStrictParentOfType<KtIfExpression>()?.takeIf { it.condition == result }
        if (ifExpression != null) ConstantConditionIfFix.applyFixIfSingle(ifExpression, updater)
    }

    override fun getFamilyName(): String = KotlinBundle.message("simplify.comparison")
}
