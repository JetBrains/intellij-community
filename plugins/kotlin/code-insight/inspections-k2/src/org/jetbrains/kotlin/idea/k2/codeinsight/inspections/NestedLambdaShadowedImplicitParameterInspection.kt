// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.findTopmostParentInFile
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeInsight.intentions.fake.ReplaceItWithExplicitFunctionLiteralParamIntention
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.addExplicitItParameter
import org.jetbrains.kotlin.idea.codeinsight.utils.callExpression
import org.jetbrains.kotlin.idea.codeinsight.utils.getImplicitLambdaParameterSymbol
import org.jetbrains.kotlin.idea.codeinsight.utils.isCalling
import org.jetbrains.kotlin.idea.codeinsight.utils.nonShadowingScopeFunctions
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypesAndPredicate
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.referenceExpressionVisitor


internal class NestedLambdaShadowedImplicitParameterInspection :
    KotlinApplicableInspectionBase<KtNameReferenceExpression, NestedLambdaShadowedImplicitParameterInspection.Context>() {
    class Context(
        val outerLambdaPointer: SmartPsiElementPointer<KtLambdaExpression>,
    )

    private fun getProblemDescription(): @InspectionMessage String {
        return KotlinBundle.message("implicit.parameter.it.of.enclosing.lambda.is.shadowed")
    }

    override fun InspectionManager.createProblemDescriptor(
        element: KtNameReferenceExpression,
        context: Context,
        rangeInElement: TextRange?,
        onTheFly: Boolean
    ): ProblemDescriptor {
        return createProblemDescriptor(
            /* psiElement = */ element,
            /* rangeInElement = */ rangeInElement,
            /* descriptionTemplate = */ getProblemDescription(),
            /* highlightType = */ ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            /* onTheFly = */ onTheFly,
            /* ...fixes = */ RenameOuterLambdaParameterFix(context.outerLambdaPointer),
            IntentionWrapper(ReplaceItWithExplicitFunctionLiteralParamIntention()),
        )
    }

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): KtVisitor<*, *> {
         return referenceExpressionVisitor { referenceExpression ->
            if (referenceExpression is KtNameReferenceExpression) {
                visitTargetElement(referenceExpression, holder, isOnTheFly)
            }
        }
    }

    override fun isApplicableByPsi(element: KtNameReferenceExpression): Boolean {
        if (element.getReferencedNameAsName() != StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME) return false
        val lambdaWithNoDeclaredParameters = element.getParentOfTypesAndPredicate(true, KtLambdaExpression::class.java) {
            it.valueParameters.isEmpty()
        }
        return lambdaWithNoDeclaredParameters != null
    }

    /**
     * Report warning if:
     * — referenced expression is an implicit `it` reference
     * — some outer lambda also has an implicit `it` that has usages
     * — owner of the `it` parameter is not a scope function (`also`, `let`, `takeIf`, `takeUnless`) called on an outer `it`
     */
    context(KaSession)
    override fun prepareContext(element: KtNameReferenceExpression): Context? {
        analyze(element) {
            val enclosingLambdaImplicitItSymbol = element.getImplicitLambdaParameterSymbol()
            if (enclosingLambdaImplicitItSymbol == null) return null
            val enclosingLambda = enclosingLambdaImplicitItSymbol.psi?.getStrictParentOfType<KtLambdaExpression>() ?: return null
            if (enclosingLambda.isAllowedScopeFunctionLambdaArgument()) return null

            val outermostLambdaWithoutParams = enclosingLambda.findTopmostParentInFile {
                it is KtLambdaExpression && it.valueParameters.isEmpty()
            } as? KtLambdaExpression ?: return null

            var parentLambdaWithUsedImplicitIt: KtLambdaExpression? = null
            outermostLambdaWithoutParams.anyDescendantOfType<KtNameReferenceExpression> { referenceExpression ->
                if (referenceExpression.getReferencedNameAsName() != StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME)
                    return@anyDescendantOfType false
                if (PsiTreeUtil.isAncestor(enclosingLambda, referenceExpression, false))
                    return@anyDescendantOfType false
                val implicitItSymbol = referenceExpression.getImplicitLambdaParameterSymbol()
                if (implicitItSymbol == null)
                    return@anyDescendantOfType false
                if (implicitItSymbol != enclosingLambdaImplicitItSymbol) {
                    parentLambdaWithUsedImplicitIt = implicitItSymbol.psi?.getStrictParentOfType<KtLambdaExpression>()
                    true
                } else {
                    false
                }
            }
            return parentLambdaWithUsedImplicitIt?.let { Context(it.createSmartPointer()) }
        }
    }

    context(KaSession)
    private fun KtLambdaExpression.isAllowedScopeFunctionLambdaArgument(): Boolean {
        val qualifiedExpression = getStrictParentOfType<KtQualifiedExpression>() ?: return false
        if (qualifiedExpression.receiverExpression.text != StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME.identifier) return false
        return qualifiedExpression.callExpression?.isCalling(nonShadowingScopeFunctions.asSequence()) == true
    }
}

private class RenameOuterLambdaParameterFix(
    private val outerLambda: SmartPsiElementPointer<KtLambdaExpression>
) : KotlinModCommandQuickFix<KtNameReferenceExpression>() {
    override fun getFamilyName(): @IntentionFamilyName String {
        return KotlinBundle.message("add.explicit.parameter.to.outer.lambda.fix.text")
    }

    override fun applyFix(
        project: Project,
        element: KtNameReferenceExpression,
        updater: ModPsiUpdater
    ) {
        val lambdaExpression = outerLambda.dereference()?.let(updater::getWritable) ?: return
        val lambdaParameter = lambdaExpression.addExplicitItParameter()
        updater.rename(lambdaParameter, listOf())
    }
}
