// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix

import com.intellij.codeInspection.util.IntentionFamilyName
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
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypeAndBranch
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

@ApiStatus.Internal
class SimplifyExpressionFix(
    psiElement: KtExpression,
    constantExpressionValue: ConstantExpressionValue,
    private val familyName: @IntentionFamilyName String = KotlinBundle.message("simplify.fix.text"),
) : KotlinPsiUpdateModCommandAction.ElementBased<KtExpression, ConstantExpressionValue>(psiElement, constantExpressionValue) {

    override fun invoke(
        actionContext: ActionContext,
        element: KtExpression,
        elementContext: ConstantExpressionValue,
        updater: ModPsiUpdater,
    ) {
        val replacement = KtPsiFactory(element.project).createExpression(elementContext.toExpressionText())
        val result = (element.getParentOfTypeAndBranch<KtQualifiedExpression> { selectorExpression } ?: element).replaced(replacement)

        val booleanExpression = result.getNonStrictParentOfType<KtBinaryExpression>()
        if (booleanExpression != null && areThereExpressionsToBeSimplified(booleanExpression)) {
            performSimplification(booleanExpression)
        } else {
            removeRedundantAssertion(result)
        }

        val ifExpression = result.getStrictParentOfType<KtIfExpression>()?.takeIf { it.condition == result }
        if (ifExpression != null) ConstantConditionIfFix.applyFixIfSingle(ifExpression, updater)
    }

    override fun getFamilyName(): String = familyName
}

@ApiStatus.Internal
sealed class ConstantExpressionValue() {
    class BooleanValue(val value: Boolean) : ConstantExpressionValue()
    class IntegerValue(val value: Int) : ConstantExpressionValue()
    object NullValue : ConstantExpressionValue()

    internal fun toExpressionText(): String = when (this) {
        is BooleanValue -> value.toString()
        is IntegerValue -> value.toString()
        NullValue -> "null"
    }

    companion object {
        fun of(value: Boolean): BooleanValue = BooleanValue(value)
        fun of(value: Int): IntegerValue = IntegerValue(value)
        fun of(value: Nothing?): NullValue = NullValue
    }
}
