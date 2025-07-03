// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.psi.PsiComment
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsight.utils.setType
import org.jetbrains.kotlin.idea.codeinsights.impl.base.CallableReturnTypeUpdaterUtils
import org.jetbrains.kotlin.idea.codeinsights.impl.base.asQuickFix
import org.jetbrains.kotlin.idea.intentions.SpecifyExplicitLambdaSignatureIntentionBase
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

internal class FunctionWithLambdaExpressionBodyInspection : AbstractKotlinInspection() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : KtVisitorVoid() {
        override fun visitNamedFunction(function: KtNamedFunction) {
            check(function)
        }

        override fun visitPropertyAccessor(accessor: KtPropertyAccessor) {
            if (accessor.isSetter) return
            if (accessor.returnTypeReference != null) return
            check(accessor)
        }

        private fun check(element: KtDeclarationWithBody) {
            val callableDeclaration = element.getNonStrictParentOfType<KtCallableDeclaration>() ?: return
            if (callableDeclaration.typeReference != null) return
            val lambda = element.bodyExpression as? KtLambdaExpression ?: return
            val functionLiteral = lambda.functionLiteral
            if (functionLiteral.arrow != null || functionLiteral.valueParameterList != null) return
            val lambdaBody = functionLiteral.bodyBlockExpression ?: return

            val used = ReferencesSearch.search(callableDeclaration).findAll().any()
            val typeInfo = analyze(callableDeclaration) {
                CallableReturnTypeUpdaterUtils.getTypeInfo(callableDeclaration)
            }
            val fixes = listOfNotNull(
                CallableReturnTypeUpdaterUtils.SpecifyExplicitTypeQuickFix(callableDeclaration, typeInfo),
                AddArrowIntention(),
                if (!used &&
                    lambdaBody.statements.size == 1 &&
                    lambdaBody.allChildren.none { it is PsiComment }
                )
                    RemoveBracesFix(lambda)
                else
                    null,
                if (!used) WrapRunFix(lambda) else null
            ).map { it.asQuickFix() }
            holder.registerProblem(
                lambda,
                KotlinBundle.message("inspection.function.with.lambda.expression.body.display.name"),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                *fixes.toTypedArray()
            )
        }
    }

    private class AddArrowIntention : SpecifyExplicitLambdaSignatureIntentionBase()

    private class RemoveBracesFix(element: KtLambdaExpression) : PsiUpdateModCommandAction<KtLambdaExpression>(element) {
        override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("remove.braces.fix.text")

        override fun invoke(
            context: ActionContext,
            element: KtLambdaExpression,
            updater: ModPsiUpdater
        ) {
            val singleStatement = element.functionLiteral.bodyExpression?.statements?.singleOrNull() ?: return
            val replaced = element.replaced(singleStatement)
            replaced.setTypeIfNeed()
        }
    }

    private class WrapRunFix(element: KtLambdaExpression) : PsiUpdateModCommandAction<KtLambdaExpression>(element) {
        override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("wrap.run.fix.text")

        override fun invoke(
            context: ActionContext,
            element: KtLambdaExpression,
            updater: ModPsiUpdater
        ) {
            val body = element.functionLiteral.bodyExpression ?: return
            val replaced = element.replaced(KtPsiFactory(element.project).createExpressionByPattern("run { $0 }", body.allChildren))
            replaced.setTypeIfNeed()
        }
    }
}

private fun KtExpression.setTypeIfNeed() {
    val declaration = getStrictParentOfType<KtCallableDeclaration>() ?: return
    analyze(this) {
        val returnType = declaration.returnType
        if (returnType.isNothingType) {
            declaration.setType(returnType)
        }
    }
}
