// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.DefaultTypeClassIds
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.singleVariableAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.ForEachParameterNotUsedInspection.UnusedForEachParameterInfo
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.refactoring.moveFunctionLiteralOutsideParentheses
import org.jetbrains.kotlin.idea.refactoring.util.setParameterListIfAny
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.*

internal class ForEachParameterNotUsedInspection :
    KotlinApplicableInspectionBase<KtCallExpression, UnusedForEachParameterInfo>() {

    override fun InspectionManager.createProblemDescriptor(
        element: KtCallExpression,
        context: UnusedForEachParameterInfo,
        rangeInElement: TextRange?,
        onTheFly: Boolean
    ): ProblemDescriptor = createProblemDescriptor(
        /* psiElement = */ element,
        /* rangeInElement = */ rangeInElement,
        /* descriptionTemplate = */ KotlinBundle.message("loop.parameter.0.is.unused", context.paramName),
        /* highlightType = */ ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
        /* onTheFly = */ onTheFly,
        /* ...fixes = */ *createQuickFixes(element, context).toTypedArray()
    )

    override fun getApplicableRanges(element: KtCallExpression): List<TextRange> {
        return ApplicabilityRanges.calleeExpression(element)
    }

    private fun createQuickFixes(
        element: KtCallExpression,
        context: UnusedForEachParameterInfo,
    ): Collection<KotlinModCommandQuickFix<KtCallExpression>> {
        val fixes = mutableListOf<KotlinModCommandQuickFix<KtCallExpression>>()
        if (element.parent is KtDotQualifiedExpression) {
            // Replace with repeat
            fixes += object : KotlinModCommandQuickFix<KtCallExpression>() {
                override fun getFamilyName(): @IntentionFamilyName String =
                    KotlinBundle.message("replace.with.repeat.fix.family.name")

                override fun applyFix(project: Project, element: KtCallExpression, updater: ModPsiUpdater) {
                    val qualifiedExpression = element.parent as? KtDotQualifiedExpression ?: return
                    val receiverExpression = qualifiedExpression.receiverExpression
                    val sizeText = analyze(receiverExpression) {
                        val receiverType = receiverExpression.expressionType
                        if (receiverType?.isSubtypeOf(StandardClassIds.Collection) == true)
                            "size"
                        else if (receiverType?.isSubtypeOf(DefaultTypeClassIds.CHAR_SEQUENCE) == true)
                            "length"
                        else
                            "count()"
                    }
                    val lambdaExpression = context.lambdaExpressionReference.dereference()?.let(updater::getWritable) ?: return
                    val psiFactory = KtPsiFactory(project)
                    val replacement = psiFactory.createExpressionByPattern(
                        "repeat($0.$sizeText, $1)",
                        receiverExpression,
                        lambdaExpression
                    )
                    val result = qualifiedExpression.replaced(replacement) as KtCallExpression
                    result.moveFunctionLiteralOutsideParentheses(updater::moveCaretTo)
                }
            }
        }
        // Introduce anonymous parameter
        fixes += object : KotlinModCommandQuickFix<KtCallExpression>() {
            override fun getFamilyName(): @IntentionFamilyName String =
                KotlinBundle.message("introduce.anonymous.parameter.fix.family.name")

            override fun applyFix(project: Project, element: KtCallExpression, updater: ModPsiUpdater) {
                val lambdaExpression = context.lambdaExpressionReference.dereference()?.let(updater::getWritable) ?: return
                val psiFactory = KtPsiFactory(project)
                val newParameterList = psiFactory.createLambdaParameterList("_")
                lambdaExpression.functionLiteral.setParameterListIfAny(psiFactory, newParameterList)
            }
        }
        return fixes
    }

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ) = object : KtVisitorVoid() {
        override fun visitCallExpression(expression: KtCallExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }
    }

    override fun KaSession.prepareContext(element: KtCallExpression): UnusedForEachParameterInfo? {
        // Synthetic check: ...forEach { }
        val calleeExpression = element.calleeExpression as? KtNameReferenceExpression
        if (calleeExpression?.getReferencedName() != FOREACH) return null
        val lambda = element.lambdaArguments.singleOrNull()?.getLambdaExpression()
        if (lambda == null || lambda.functionLiteral.arrow != null) return null

        // Check if the callee is forEach of interest.
        val callInfo = element.resolveToCall()?.singleFunctionCallOrNull() ?: return null
        val callableId = callInfo.partiallyAppliedSymbol.signature.callableId ?: return null
        if (callableId != COLLECTIONS_FOREACH && callableId != SEQUENCES_FOREACH && callableId != TEXT_FOREACH)
            return null

        // Check if the implicit lambda parameter is indeed not used.
        if (lambda.useLambdaParameter()) return null
        val anonymousFunctionSymbol = lambda.functionLiteral.symbol
        val lambdaParameterSymbol = anonymousFunctionSymbol.valueParameters.singleOrNull() ?: return null
        return UnusedForEachParameterInfo(
            lambda.createSmartPointer(),
            lambdaParameterSymbol.name.identifier
        )
    }

    context(KaSession)
    private fun KtLambdaExpression.useLambdaParameter(): Boolean {
        var used = false
        bodyExpression?.acceptChildren(object : KtVisitorVoid() {
            override fun visitElement(element: PsiElement) {
                if (used) return
                if (element.children.isNotEmpty()) {
                    element.acceptChildren(this)
                } else {
                    val symbol = (element as? KtElement)
                        ?.resolveToCall()
                        ?.singleVariableAccessCall()
                        ?.partiallyAppliedSymbol
                        ?.symbol as? KaValueParameterSymbol
                    // it, which belongs to the lambda in question
                    used = symbol?.isImplicitLambdaParameter == true &&
                            symbol.containingDeclaration?.psi == this@useLambdaParameter.functionLiteral
                }
            }
        })
        return used
    }

    internal data class UnusedForEachParameterInfo(
        val lambdaExpressionReference: SmartPsiElementPointer<KtLambdaExpression>,
        val paramName: String,
    )
}

private const val FOREACH = "forEach"
private val FOREACH_NAME = Name.identifier(FOREACH)
private val COLLECTIONS_FOREACH = CallableId(StandardNames.COLLECTIONS_PACKAGE_FQ_NAME, FOREACH_NAME)
private val SEQUENCES_FOREACH = CallableId(StandardClassIds.BASE_SEQUENCES_PACKAGE, FOREACH_NAME)
private val TEXT_FOREACH = CallableId(StandardNames.TEXT_PACKAGE_FQ_NAME, FOREACH_NAME)