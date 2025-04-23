// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.base.analysis.api.utils.allOverriddenSymbolsWithSelf
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.callExpression
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import org.jetbrains.kotlin.types.Variance

private const val PUT_METHOD_NAME = "put"
private val collectionsSetFqName = FqName("kotlin.collections.set")
private val mutableMapPutFqName = StandardNames.FqNames.mutableMap.child(Name.identifier(PUT_METHOD_NAME))

internal class ReplacePutWithAssignmentInspection : KotlinApplicableInspectionBase.Simple<KtDotQualifiedExpression, ReplacePutWithAssignmentInspection.Context>() {
    data class Context(val assignment: KtBinaryExpression)

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitor<*, *> = dotQualifiedExpressionVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    override fun isApplicableByPsi(element: KtDotQualifiedExpression): Boolean {
        if (element.receiverExpression is KtSuperExpression) return false

        val callExpression = element.callExpression ?: return false
        if (callExpression.valueArguments.size != 2) return false

        val calleeExpression = callExpression.calleeExpression as? KtNameReferenceExpression ?: return false
        return calleeExpression.getReferencedName() == PUT_METHOD_NAME
    }

    @OptIn(KaExperimentalApi::class)
    override fun KaSession.prepareContext(element: KtDotQualifiedExpression): Context? {
        if (element.isUsedAsExpression) return null

        val resolvedCall = element.resolveToCall()?.successfulFunctionCallOrNull() ?: return null
        val receiverType = resolvedCall.partiallyAppliedSymbol.dispatchReceiver?.type ?: return null
        if (!receiverType.isSubtypeOf(StandardClassIds.MutableMap)) return null

        val functionSymbol = resolvedCall.partiallyAppliedSymbol.symbol
        if (functionSymbol.allOverriddenSymbolsWithSelf.none { it.isMutableMapPutFunction() }) return null

        val receiverTypeText = element.receiverExpression.expressionType?.render(position = Variance.IN_VARIANCE) ?: return null
        val assignment = createAssignmentExpression(element)

        val codeFragment = KtPsiFactory(
            element.project,
            markGenerated = false,
        ).createBlockCodeFragment("""
            fun foo(${element.receiverExpression.text}: $receiverTypeText) {
                ${assignment!!.text}
            }
        """, element)

        val arrayAccessExpression = codeFragment.findDescendantOfType<KtArrayAccessExpression>()
        val resolvedArrayAccessExpression = arrayAccessExpression?.resolveToCall()?.singleFunctionCallOrNull() ?: return null
        if (resolvedArrayAccessExpression.symbol.callableId?.asSingleFqName() != collectionsSetFqName) return null

        return Context(assignment)
    }

    override fun getProblemDescription(
        element: KtDotQualifiedExpression,
        context: Context,
    ): @InspectionMessage String = KotlinBundle.message("map.put.should.be.converted.to.assignment")

    override fun createQuickFix(
        element: KtDotQualifiedExpression,
        context: Context,
    ): KotlinModCommandQuickFix<KtDotQualifiedExpression> = object : KotlinModCommandQuickFix<KtDotQualifiedExpression>() {
        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("convert.put.to.assignment")

        override fun applyFix(
            project: Project,
            element: KtDotQualifiedExpression,
            updater: ModPsiUpdater,
        ) {
            element.replace(context.assignment)
        }
    }
}

private fun createAssignmentExpression(element: KtDotQualifiedExpression): KtBinaryExpression? {
    val valueArguments = element.callExpression?.valueArguments ?: return null
    val firstArg = valueArguments[0]?.getArgumentExpression() ?: return null
    val secondArg = valueArguments[1]?.getArgumentExpression() ?: return null
    val label = if (secondArg is KtLambdaExpression) {
        val returnLabel = secondArg.findDescendantOfType<KtReturnExpression>()?.getLabelName()
        if (returnLabel == PUT_METHOD_NAME) "$PUT_METHOD_NAME@ " else ""
    }
    else ""
    return KtPsiFactory(element.project).createExpressionByPattern(
        "$0[$1] = $label$2",
        element.receiverExpression, firstArg, secondArg,
        reformat = false
    ) as? KtBinaryExpression
}

private fun KaCallableSymbol.isMutableMapPutFunction(): Boolean =
    callableId?.asSingleFqName() == mutableMapPutFqName
