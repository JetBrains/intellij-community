// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic.SenselessComparison
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.SimplifyBooleanWithConstantsUtils.areThereExpressionsToBeSimplified
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.SimplifyBooleanWithConstantsUtils.performSimplification
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.SimplifyBooleanWithConstantsUtils.removeRedundantAssertion
import org.jetbrains.kotlin.idea.quickfix.fixes.SimplifyComparisonFixFactory.SimplifyComparisonFix.ElementContext
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtDeclarationWithBody
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

object SimplifyComparisonFixFactory {
    val simplifyComparisonFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: SenselessComparison ->
        val expression = diagnostic.psi.takeIf { it.getStrictParentOfType<KtDeclarationWithBody>() != null }
            ?: return@ModCommandBased emptyList()
        val compareResult = diagnostic.compareResult

        listOf(SimplifyComparisonFix(expression, ElementContext(compareResult)))
    }

    private class SimplifyComparisonFix(psiElement: KtExpression, context: ElementContext) :
        KotlinModCommandAction<KtExpression, ElementContext>(psiElement, context) {

        class ElementContext(val compareResult: Boolean) : KotlinModCommandAction.ElementContext

        override fun invoke(context: ActionContext,
                            element: KtExpression,
                            elementContext: ElementContext,
                            updater: ModPsiUpdater) {
            val replacement = KtPsiFactory(element.project).createExpression("${elementContext.compareResult}")
            val result = element.replaced(replacement)

            val booleanExpression = result.getNonStrictParentOfType<KtBinaryExpression>()
            if (booleanExpression != null && areThereExpressionsToBeSimplified(booleanExpression)) {
                performSimplification(booleanExpression)
            }
            else {
                removeRedundantAssertion(result)
            }

            // TODO: after implementing KTIJ-29044 ConstantConditionIfInspection
            //val ifExpression = result.getStrictParentOfType<KtIfExpression>()?.takeIf { it.condition == result }
            //if (ifExpression != null) ConstantConditionIfInspection.Util.applyFixIfSingle(ifExpression)
        }

        override fun getFamilyName(): String = KotlinBundle.message("simplify.comparison")
    }
}