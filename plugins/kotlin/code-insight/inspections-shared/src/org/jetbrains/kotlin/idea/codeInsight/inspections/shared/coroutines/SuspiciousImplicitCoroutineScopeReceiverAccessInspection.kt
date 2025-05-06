// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared.coroutines

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.options.OptPane
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.KaImplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.successfulVariableAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaReceiverParameterSymbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.utils.getCallExpressionSymbol
import org.jetbrains.kotlin.idea.codeinsight.utils.isInlinedArgument
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector
import org.jetbrains.kotlin.psi.psiUtil.hasSuspendModifier
import org.jetbrains.kotlin.resolve.calls.util.getCalleeExpressionIfAny

internal class SuspiciousImplicitCoroutineScopeReceiverAccessInspection() : KotlinApplicableInspectionBase<KtExpression, Unit>() {
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

    override fun KaSession.prepareContext(element: KtExpression): Unit? {
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

        return Unit
    }

    context(KaSession) 
    private fun KaType.isCoroutineScopeType(acceptSubtypes: Boolean = true): Boolean {
        val coroutineScopeType = findClass(COROUTINE_SCOPE_CLASS_ID)?.defaultType ?: return false

        return if (acceptSubtypes) {
            this.isSubtypeOf(coroutineScopeType)
        } else {
            this.semanticallyEquals(coroutineScopeType)
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

                // Skip lambdas which happen to be inline
                if (isInlinedArgument(current)) {
                    current = current.parent
                    continue
                }

                // Resolve the outer call of the lambda
                val callExpressionSymbol = getCallExpressionSymbol(current)
                if (callExpressionSymbol != null) {
                    // Check if the matching parameter's return type is a suspend type
                    val (_, argumentSymbol) = callExpressionSymbol
                    val parameterType = argumentSymbol.returnType

                    if (parameterType.isSuspendFunctionType) {
                        return true
                    }
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

    override fun InspectionManager.createProblemDescriptor(
        element: KtExpression,
        context: Unit,
        rangeInElement: TextRange?,
        onTheFly: Boolean
    ): ProblemDescriptor {
        return createProblemDescriptor(
            /* psiElement = */ element,
            /* rangeInElement = */ rangeInElement,
            /* descriptionTemplate = */ KotlinBundle.message("inspection.suspicious.implicit.coroutine.scope.receiver.description"),
            /* highlightType = */ ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            /* onTheFly = */ onTheFly,
            /* ...fixes = */
        )
    }
}

private val COROUTINE_SCOPE_CLASS_ID: ClassId = ClassId.fromString("kotlinx/coroutines/CoroutineScope")