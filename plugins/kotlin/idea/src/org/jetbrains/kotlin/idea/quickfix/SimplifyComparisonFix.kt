// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.DiagnosticWithParameters2
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ConstantConditionIfFix
import org.jetbrains.kotlin.idea.intentions.SimplifyBooleanWithConstantsIntention
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

class SimplifyComparisonFix(element: KtExpression, val value: Boolean) : KotlinPsiUpdateModCommandAction.ElementBased<KtExpression, Unit>(element, Unit) {
    override fun getFamilyName(): String =
        KotlinBundle.message("simplify.comparison")

    override fun invoke(actionContext: ActionContext, element: KtExpression, elementContext: Unit, updater: ModPsiUpdater) {
        val replacement = KtPsiFactory(element.project).createExpression("$value")
        val result = element.replaced(replacement)

        val booleanExpression = result.getNonStrictParentOfType<KtBinaryExpression>()
        val simplifyIntention = SimplifyBooleanWithConstantsIntention()
        if (booleanExpression != null && simplifyIntention.isApplicableTo(booleanExpression)) {
            simplifyIntention.applyTo(booleanExpression, editor = null)
        } else {
            simplifyIntention.removeRedundantAssertion(result)
        }

        val ifExpression = result.getStrictParentOfType<KtIfExpression>()?.takeIf { it.condition == result }
        if (ifExpression != null) ConstantConditionIfFix.applyFixIfSingle(ifExpression, updater)
    }

    companion object : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val expression = diagnostic.psiElement as? KtExpression ?: return null
            val value = (diagnostic as? DiagnosticWithParameters2<*, *, *>)?.b as? Boolean ?: return null
            return SimplifyComparisonFix(expression, value).asIntention()
        }
    }
}