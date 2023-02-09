// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections.conventionNameCalls

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import org.jetbrains.kotlin.cfg.containingDeclarationForPseudocode
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.util.match
import org.jetbrains.kotlin.idea.caches.resolve.analyzeAsReplacement
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractApplicabilityBasedInspection
import org.jetbrains.kotlin.idea.inspections.KotlinEqualsBetweenInconvertibleTypesInspection
import org.jetbrains.kotlin.idea.intentions.callExpression
import org.jetbrains.kotlin.idea.intentions.conventionNameCalls.isAnyEquals
import org.jetbrains.kotlin.idea.intentions.isOperatorOrCompatible
import org.jetbrains.kotlin.idea.intentions.isReceiverExpressionWithValue
import org.jetbrains.kotlin.idea.resolve.dataFlowValueFactory
import org.jetbrains.kotlin.idea.util.calleeTextRangeInThis
import org.jetbrains.kotlin.lexer.KtSingleValueToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getLastParentOfTypeInRow
import org.jetbrains.kotlin.resolve.calls.util.getFirstArgumentExpression
import org.jetbrains.kotlin.resolve.calls.util.getReceiverExpression
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.jetbrains.kotlin.resolve.calls.model.ArgumentMatch
import org.jetbrains.kotlin.resolve.calls.model.isReallySuccess
import org.jetbrains.kotlin.resolve.calls.smartcasts.getKotlinTypeWithPossibleSmartCastToFP
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.expressions.OperatorConventions
import org.jetbrains.kotlin.types.isNullabilityFlexible
import org.jetbrains.kotlin.types.typeUtil.*
import org.jetbrains.kotlin.util.OperatorNameConventions
import org.jetbrains.kotlin.psi.psiUtil.parents

class ReplaceCallWithBinaryOperatorInspection : AbstractApplicabilityBasedInspection<KtDotQualifiedExpression>(
    KtDotQualifiedExpression::class.java
) {

    private fun IElementType.inverted(): KtSingleValueToken? = when (this) {
        KtTokens.LT -> KtTokens.GT
        KtTokens.GT -> KtTokens.LT

        KtTokens.GTEQ -> KtTokens.LTEQ
        KtTokens.LTEQ -> KtTokens.GTEQ

        else -> null
    }

    override fun isApplicable(element: KtDotQualifiedExpression): Boolean {
        val calleeExpression = element.callExpression?.calleeExpression as? KtSimpleNameExpression ?: return false
        if (operation(calleeExpression) == null) return false

        val context = element.analyze(BodyResolveMode.PARTIAL)
        val resolvedCall = element.callExpression?.getResolvedCall(context) ?: return false
        if (!resolvedCall.isReallySuccess()) return false
        if (resolvedCall.call.typeArgumentList != null) return false
        val argument = resolvedCall.call.valueArguments.singleOrNull() ?: return false
        if ((resolvedCall.getArgumentMapping(argument) as ArgumentMatch).valueParameter.index != 0) return false

        if (!element.isReceiverExpressionWithValue()) return false

        val (expressionToBeReplaced, newExpression) = getReplacementExpression(element) ?: return false
        val newContext = newExpression.analyzeAsReplacement(expressionToBeReplaced, context)
        return newContext.diagnostics.noSuppression().forElement(newExpression).isEmpty()
    }

    override fun inspectionHighlightRangeInElement(element: KtDotQualifiedExpression) = element.calleeTextRangeInThis()

    override fun inspectionHighlightType(element: KtDotQualifiedExpression): ProblemHighlightType {
        val calleeExpression = element.callExpression?.calleeExpression as? KtSimpleNameExpression
        val identifier = calleeExpression?.getReferencedNameAsName()
        if (identifier == OperatorNameConventions.EQUALS) {
            val context = element.analyze(BodyResolveMode.PARTIAL)
            if (element.receiverExpression.getType(context)?.isNullabilityFlexible() == true) {
                return ProblemHighlightType.INFORMATION
            }
        }
        val isFloatingPointNumberEquals = calleeExpression?.isFloatingPointNumberEquals() ?: false
        return if (isFloatingPointNumberEquals) {
            ProblemHighlightType.INFORMATION
        } else if (identifier == OperatorNameConventions.EQUALS || identifier == OperatorNameConventions.COMPARE_TO) {
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING
        } else {
            ProblemHighlightType.INFORMATION
        }
    }

    override fun inspectionText(element: KtDotQualifiedExpression) = KotlinBundle.message("call.replaceable.with.binary.operator")

    override val defaultFixText: String get() = KotlinBundle.message("replace.with.binary.operator")

    override fun fixText(element: KtDotQualifiedExpression): String {
        val calleeExpression = element.callExpression?.calleeExpression as? KtSimpleNameExpression ?: return defaultFixText
        if (calleeExpression.isFloatingPointNumberEquals()) {
            return KotlinBundle.message("replace.total.order.equality.with.ieee.754.equality")
        }

        val operation = operation(calleeExpression) ?: return defaultFixText
        return KotlinBundle.message("replace.with.0", operation.value)
    }

    override fun applyTo(element: KtDotQualifiedExpression, project: Project, editor: Editor?) {
        val (expressionToBeReplaced, newExpression) = getReplacementExpression(element) ?: return
        expressionToBeReplaced.replace(newExpression)
    }

    private fun getReplacementExpression(element: KtDotQualifiedExpression): Pair<KtExpression, KtExpression>? {
        val callExpression = element.callExpression ?: return null
        val calleeExpression = callExpression.calleeExpression as? KtSimpleNameExpression ?: return null
        val operation = operation(calleeExpression) ?: return null
        val argument = callExpression.valueArguments.single().getArgumentExpression() ?: return null
        val receiver = element.receiverExpression

        val psiFactory = KtPsiFactory(element.project)
        return when (operation) {
            KtTokens.EXCLEQ -> {
                val prefixExpression = element.getWrappingPrefixExpressionIfAny() ?: return null
                val newExpression = psiFactory.createExpressionByPattern("$0 != $1", receiver, argument, reformat = false)
                prefixExpression to newExpression
            }
            in OperatorConventions.COMPARISON_OPERATIONS -> {
                val binaryParent = element.parent as? KtBinaryExpression ?: return null
                val newExpression = psiFactory.createExpressionByPattern("$0 ${operation.value} $1", receiver, argument, reformat = false)
                binaryParent to newExpression
            }
            else -> {
                val newExpression = psiFactory.createExpressionByPattern("$0 ${operation.value} $1", receiver, argument, reformat = false)
                element to newExpression
            }
        }
    }

    private fun PsiElement.getWrappingPrefixExpressionIfAny() =
        (getLastParentOfTypeInRow<KtParenthesizedExpression>() ?: this).parent as? KtPrefixExpression

    private fun operation(calleeExpression: KtSimpleNameExpression): KtSingleValueToken? {
        val identifier = calleeExpression.getReferencedNameAsName()
        val dotQualified =
          calleeExpression.parents.match(KtCallExpression::class, last = KtDotQualifiedExpression::class) ?: return null
        val isOperatorOrCompatible by lazy {
            (calleeExpression.resolveToCall()?.resultingDescriptor as? FunctionDescriptor)?.isOperatorOrCompatible == true
        }
        return when (identifier) {
            OperatorNameConventions.EQUALS -> {
                if (!dotQualified.isAnyEquals()) return null
                with(KotlinEqualsBetweenInconvertibleTypesInspection) {
                    val receiver = dotQualified.receiverExpression
                    val argument = dotQualified.callExpression?.valueArguments?.singleOrNull()?.getArgumentExpression()
                    if (dotQualified.analyze(BodyResolveMode.PARTIAL).isInconvertibleTypes(receiver, argument)) return null
                }
                val prefixExpression = dotQualified.getWrappingPrefixExpressionIfAny()
                if (prefixExpression != null && prefixExpression.operationToken == KtTokens.EXCL) KtTokens.EXCLEQ
                else KtTokens.EQEQ
            }
            OperatorNameConventions.COMPARE_TO -> {
                if (!isOperatorOrCompatible) return null
                // callee -> call -> DotQualified -> Binary
                val binaryParent = dotQualified.parent as? KtBinaryExpression ?: return null
                val notZero = when {
                    binaryParent.right?.text == "0" -> binaryParent.left
                    binaryParent.left?.text == "0" -> binaryParent.right
                    else -> return null
                }
                if (notZero != dotQualified) return null
                val token = binaryParent.operationToken as? KtSingleValueToken ?: return null
                if (token in OperatorConventions.COMPARISON_OPERATIONS) {
                    if (notZero == binaryParent.left) token else token.inverted()
                } else {
                    null
                }
            }
            else -> {
                if (!isOperatorOrCompatible) return null
                OperatorConventions.BINARY_OPERATION_NAMES.inverse()[identifier]
            }
        }
    }

    private fun KtDotQualifiedExpression.isFloatingPointNumberEquals(): Boolean {
        val resolvedCall = resolveToCall() ?: return false
        val resolutionFacade = getResolutionFacade()
        val context = analyze(resolutionFacade, BodyResolveMode.PARTIAL)
        val declarationDescriptor = containingDeclarationForPseudocode?.resolveToDescriptorIfAny()
        val dataFlowValueFactory = resolutionFacade.dataFlowValueFactory
        val defaultType: (KotlinType, Set<KotlinType>) -> KotlinType = { givenType, stableTypes -> stableTypes.firstOrNull() ?: givenType }
        val receiverType = resolvedCall.getReceiverExpression()?.getKotlinTypeWithPossibleSmartCastToFP(
            context, declarationDescriptor, languageVersionSettings, dataFlowValueFactory, defaultType
        ) ?: return false
        val argumentType = resolvedCall.getFirstArgumentExpression()?.getKotlinTypeWithPossibleSmartCastToFP(
            context, declarationDescriptor, languageVersionSettings, dataFlowValueFactory, defaultType
        ) ?: return false
        return receiverType.isFpType() && argumentType.isNumericType() ||
                argumentType.isFpType() && receiverType.isNumericType()
    }

    private fun KtSimpleNameExpression.isFloatingPointNumberEquals(): Boolean {
        val dotQualified = parent.parent as? KtDotQualifiedExpression ?: return false
        return dotQualified.isFloatingPointNumberEquals()
    }

    private fun KotlinType.isFpType(): Boolean {
        return isFloat() || isDouble()
    }

    private fun KotlinType.isNumericType(): Boolean {
        return isFpType() || isByte() || isShort() || isInt() || isLong()
    }
}
