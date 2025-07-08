// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.InspectionManager
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
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.*
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypesAndPredicate
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType


internal class NestedLambdaShadowedImplicitParameterInspection :
    KotlinApplicableInspectionBase<KtNameReferenceExpression, NestedLambdaShadowedImplicitParameterInspection.Context>() {
    class Context(
        val ownerLambdaPointer: SmartPsiElementPointer<KtLambdaExpression>,
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
            /* ...fixes = */
            RenameLambdaParameterFix(
                context.ownerLambdaPointer,
                KotlinBundle.message("replace.it.with.explicit.parameter"),
            ),
            RenameLambdaParameterFix(
                context.outerLambdaPointer,
                KotlinBundle.message("add.explicit.parameter.to.outer.lambda.fix.text"),
            ),
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
     * — some other outer lambda also has an implicit `it` that has usages
     * — owner of the referenced `it` parameter is not a scope function call (`also`, `let`, `takeIf`, `takeUnless`) on an outer `it`
     */
    override fun KaSession.prepareContext(element: KtNameReferenceExpression): Context? {
        val enclosingLambdaImplicitItSymbol = element.getImplicitLambdaParameterSymbol()
        if (enclosingLambdaImplicitItSymbol == null) return null
        val ownerLambda = getOwnerLambdaExpression(enclosingLambdaImplicitItSymbol) ?: return null
        if (ownerLambda.isAllowedScopeFunctionLambdaArgument()) return null

        val outermostLambdaWithoutParams = ownerLambda.findTopmostParentInFile {
            it is KtLambdaExpression && it.valueParameters.isEmpty()
        } as? KtLambdaExpression ?: return null

        val anotherItReferenceOutside = outermostLambdaWithoutParams.findDescendantOfType<KtNameReferenceExpression> { refExpression ->
            isShadowedImplicitItOfAnotherLambda(refExpression, ownerLambda, enclosingLambdaImplicitItSymbol)
        }

        return anotherItReferenceOutside?.getImplicitLambdaParameterSymbol()?.let(::getOwnerLambdaExpression)?.let { outerLambda ->
            Context(ownerLambda.createSmartPointer(), outerLambda.createSmartPointer())
        }
    }

    context(KaSession)
    private fun KtLambdaExpression.isAllowedScopeFunctionLambdaArgument(): Boolean {
        val qualifiedExpression = getStrictParentOfType<KtQualifiedExpression>() ?: return false
        if (qualifiedExpression.receiverExpression.text != StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME.identifier) return false
        return qualifiedExpression.callExpression?.isCallingAnyOf(*scopeFunctionsList) == true
    }

    context(KaSession)
    private fun isShadowedImplicitItOfAnotherLambda(
        refExpression: KtNameReferenceExpression,
        primaryLambda: KtLambdaExpression,
        primaryLambdaItSymbol: KaValueParameterSymbol,
    ): Boolean {
        if (refExpression.getReferencedNameAsName() != StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME) return false
        // Not interested in `it` refs inside the primary lambda
        if (PsiTreeUtil.isAncestor(primaryLambda, refExpression, false)) return false
        val otherItSymbol = refExpression.getImplicitLambdaParameterSymbol() ?: return false
        if (otherItSymbol == primaryLambdaItSymbol) return false
        // If an outer `it`'s owner lambda is not an ancestor of the primary lambda, then this `it` is not shadowed
        return PsiTreeUtil.isAncestor(getOwnerLambdaExpression(otherItSymbol), primaryLambda, false)
    }

    private fun getOwnerLambdaExpression(itParameterSymbol: KaValueParameterSymbol): KtLambdaExpression? {
        return itParameterSymbol.psi?.getStrictParentOfType<KtLambdaExpression>()
    }
}

private class RenameLambdaParameterFix(
    private val lambdaExpressionReference: SmartPsiElementPointer<KtLambdaExpression>,
    private val familyName: @IntentionFamilyName String,
) : KotlinModCommandQuickFix<KtNameReferenceExpression>() {
    override fun getFamilyName(): @IntentionFamilyName String = familyName

    override fun applyFix(
        project: Project,
        element: KtNameReferenceExpression,
        updater: ModPsiUpdater
    ) {
        val lambdaExpression = lambdaExpressionReference.dereference()?.let(updater::getWritable) ?: return
        val lambdaParameter = lambdaExpression.addExplicitItParameter()
        updater.rename(lambdaParameter, listOf())
    }
}