// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared.coroutines

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.options.OptPane
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.defaultType
import org.jetbrains.kotlin.analysis.api.components.isSubtypeOf
import org.jetbrains.kotlin.analysis.api.components.semanticallyEquals
import org.jetbrains.kotlin.analysis.api.resolution.KaImplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.successfulVariableAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaReceiverParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.findClass
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.utils.getCallExpressionSymbol
import org.jetbrains.kotlin.idea.codeinsight.utils.isInlinedArgument
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.findLabelAndCall
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector
import org.jetbrains.kotlin.psi.psiUtil.hasSuspendModifier
import org.jetbrains.kotlin.resolve.calls.util.getCalleeExpressionIfAny

internal class SuspiciousImplicitCoroutineScopeReceiverAccessInspection() :
    KotlinApplicableInspectionBase<KtExpression, SuspiciousImplicitCoroutineScopeReceiverAccessInspection.Context>() {

    class Context(val receiverLabelName: Name)

    @JvmField
    var detectCoroutineScopeSubtypes: Boolean = false

    override fun getOptionsPane(): OptPane = OptPane.pane(
        OptPane.checkbox(
            ::detectCoroutineScopeSubtypes.name,
            KotlinBundle.message("inspection.suspicious.implicit.coroutine.scope.receiver.detect.subclasses.option")
        ),
    )

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitor<*, *> = expressionVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    override fun getApplicableRanges(element: KtExpression): List<TextRange> =
        ApplicabilityRange.single(element) { it.getCalleeExpressionIfAny() }

    override fun isApplicableByPsi(element: KtExpression): Boolean { // Check if the call is not qualified (implicit receiver)
        val qualifiedExpression = element.getQualifiedExpressionForSelector()
        return qualifiedExpression == null
    }

    override fun KaSession.prepareContext(element: KtExpression): Context? {
        // Resolve the call to check if it's a CoroutineScope function
        val resolvedCall = element.resolveToCall()?.let { callInfo ->
            when (element) {
                is KtCallExpression -> callInfo.successfulFunctionCallOrNull()
                is KtSimpleNameExpression -> callInfo.successfulVariableAccessCall()
                else -> null
            }
        } ?: return null

        // Check if the receiver is an implicit receiver
        val callReceiver = resolvedCall.partiallyAppliedSymbol.run { extensionReceiver ?: dispatchReceiver }
        if (callReceiver !is KaImplicitReceiverValue) return null

        // Check if the receiver type is CoroutineScope
        if (!callReceiver.type.isCoroutineScopeType(acceptSubtypes = detectCoroutineScopeSubtypes)) {
            return null
        }

        // Check that the function has CoroutineScope as extension parameter (if present)        
        val originalReceiverParameter = resolvedCall.symbol.receiverParameter
        if (originalReceiverParameter?.returnType?.isCoroutineScopeType() == false) {
            return null
        }

        val receiverOwnerSymbol = when (val receiverSymbol = callReceiver.symbol) {
            is KaClassLikeSymbol -> receiverSymbol
            is KaReceiverParameterSymbol -> receiverSymbol.owningCallableSymbol
            else -> null
        } ?: return null 

        // Check if there are any suspend lambdas between the call PSI and the implicit ContextReceiver symbol PSI
        if (!hasSuspendFunctionsInPath(element, receiverOwnerSymbol)) return null

        // Get the name for the label based on the type of receiverOwnerSymbol
        val receiverLabelName = receiverOwnerSymbol.findLabelName() ?: return null

        return Context(receiverLabelName)
    }

    context(_: KaSession)
    private fun KaType.isCoroutineScopeType(acceptSubtypes: Boolean = true): Boolean {
        val coroutineScopeType = findClass(CoroutinesIds.COROUTINE_SCOPE_CLASS_ID)?.defaultType ?: return false

        return if (acceptSubtypes) {
            this@isCoroutineScopeType.isSubtypeOf(coroutineScopeType)
        } else {
            this@isCoroutineScopeType.semanticallyEquals(coroutineScopeType)
        }
    }

    /**
     * Checks if there are any suspend functions or lambdas between the call PSI and the implicit ContextReceiver symbol PSI.
     */
    private fun KaSession.hasSuspendFunctionsInPath(element: KtExpression, receiverOwnerSymbol: KaDeclarationSymbol): Boolean {
        var current: PsiElement? = element.parent
        val receiverOwnerDeclaration = receiverOwnerSymbol.psi ?: return false

        while (current != null && current != receiverOwnerDeclaration) {
            if (current is KtFunctionLiteral) {
                if (receiverOwnerSymbol == current.symbol) return false

                // Skip lambdas which happen to be fully locally inlined
                if (isInlinedArgument(current, allowCrossinline = false)) {
                    current = current.parent
                    continue
                }

                // Check if the matching parameter's return type is a suspend type
                val (functionSymbol, argumentSymbol) = getCallExpressionSymbol(current) ?: continue
                // Resolve the outer call of the lambda
                val parameterType = argumentSymbol.returnType

                if (parameterType.isSuspendFunctionType && !isAllowedSuspendingFunction(functionSymbol)) {
                    return true
                }
            }

            if (current is KtNamedFunction) {
                if (current.modifierList?.hasSuspendModifier() == true) {
                    return true
                }
            }

            current = current.parent
        }

        return false
    }

    /**
     * Checks if [functionSymbol] is considered to be safe to use implicit `CoroutineScope` receiver.
     */
    private fun isAllowedSuspendingFunction(functionSymbol: KaFunctionSymbol): Boolean =
        when (functionSymbol.callableId) {
            CoroutinesIds.SELECT_BUILDER_INVOKE_ID,
            CoroutinesIds.SELECT_BUILDER_ON_TIMEOUT_ID -> true

            else -> false
        }

    private fun KaDeclarationSymbol.findLabelName(): Name? =
        when (val psi = psi) {
            is KtFunctionLiteral -> {
                val (labelName, _) = psi.findLabelAndCall()

                labelName
            }

            is KtNamedDeclaration -> psi.nameAsName

            else -> null
        }

    private class AddExplicitLabeledReceiverFix(private val context: Context) : KotlinModCommandQuickFix<KtExpression>() {
        override fun getFamilyName(): String = KotlinBundle.message("inspection.suspicious.implicit.coroutine.scope.receiver.add.explicit.receiver.fix.text")
        override fun applyFix(
            project: Project,
            element: KtExpression,
            updater: ModPsiUpdater
        ) {
            val expressionWithExplicitReceiver =
                KtPsiFactory(project).createExpression("this@${context.receiverLabelName}.${element.text}")
            element.replace(expressionWithExplicitReceiver)
        }
    }

    override fun InspectionManager.createProblemDescriptor(
        element: KtExpression,
        context: Context,
        rangeInElement: TextRange?,
        onTheFly: Boolean
    ): ProblemDescriptor {
        return createProblemDescriptor(
            /* psiElement = */ element,
            /* rangeInElement = */ rangeInElement,
            /* descriptionTemplate = */ KotlinBundle.message("inspection.suspicious.implicit.coroutine.scope.receiver.description"),
            /* highlightType = */ ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            /* onTheFly = */ onTheFly,
            /* fixes = */ AddExplicitLabeledReceiverFix(context),
        )
    }
}
