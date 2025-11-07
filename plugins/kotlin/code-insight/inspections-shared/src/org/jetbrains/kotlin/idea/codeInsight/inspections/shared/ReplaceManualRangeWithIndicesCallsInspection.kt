// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.successfulVariableAccessCall
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.psi.safeDeparenthesize
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.ImplicitReceiverInfo
import org.jetbrains.kotlin.idea.codeinsight.utils.LoopToCollectionTransformUtils
import org.jetbrains.kotlin.idea.codeinsight.utils.getImplicitReceiverInfo
import org.jetbrains.kotlin.idea.codeInsight.hints.RangeKtExpressionType
import org.jetbrains.kotlin.idea.codeInsight.hints.RangeKtExpressionType.DOWN_TO
import org.jetbrains.kotlin.idea.codeInsight.hints.RangeKtExpressionType.RANGE_TO
import org.jetbrains.kotlin.idea.codeInsight.hints.RangeKtExpressionType.RANGE_UNTIL
import org.jetbrains.kotlin.idea.codeInsight.hints.RangeKtExpressionType.UNTIL
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.util.match

/**
 * Inspection that suggests replacing manual ranges like `0..<arr.size` with `arr.indices` calls.
 */
class ReplaceManualRangeWithIndicesCallsInspection : AbstractRangeInspection<ReplaceManualRangeWithIndicesCallsInspection.Context>() {

    data class Context(
        val sizeCall: KtExpression?,
        val suggestElementLoop: Boolean,
        val implicitReceiverInfo: ImplicitReceiverInfo?,
    )

    override fun getProblemDescription(
        range: RangeExpression,
        context: Context
    ): @InspectionMessage String {
        return if (context.suggestElementLoop) {
            KotlinBundle.message("for.loop.over.indices.could.be.replaced.with.loop.over.elements")
        } else KotlinBundle.message("range.could.be.replaced.with.indices.call")
    }

    override fun isApplicableByPsi(range: RangeExpression): Boolean {
        // Only ascending ranges are candidates for indices replacement
        if (range.type == DOWN_TO) return false
        
        val (left, right) = range.arguments
        
        // Must start with 0 to be a candidate for indices replacement
        if (left == null || !left.isIntConstantExpression(0)) return false
        
        // Must have a valid target expression that can be extracted
        if (right == null) return false
        
        return extractTargetExpression(range.type, right) != null
    }

    override fun KaSession.prepareContext(range: RangeExpression): Context? {
        val (_, right) = range.arguments
        
        // Must end with a size/length call
        val sizeCall = right?.let { sizeOrLengthCall(range.type, it) } ?: return null
        val explicitReceiver = (sizeCall as? KtQualifiedExpression)?.receiverExpression

        // Only support simple receivers to avoid complex transformations
        if (explicitReceiver != null && !isSimpleReceiver(explicitReceiver)) return null

        val suggestElementLoop = shouldSuggestElementLoop(range, explicitReceiver)
        val implicitReceiverInfo = if (explicitReceiver == null) sizeCall.getImplicitReceiverInfo() else null

        return Context(sizeCall, suggestElementLoop, implicitReceiverInfo)
    }

    private fun isSimpleReceiver(receiver: KtExpression): Boolean =
        receiver is KtSimpleNameExpression || receiver is KtThisExpression

    private fun KaSession.sizeOrLengthCall(type: RangeKtExpressionType, expression: KtExpression): KtExpression? {
        val target = extractTargetExpression(type, expression) ?: return null
        val selector = (target as? KtDotQualifiedExpression)?.selectorExpression ?: target
        val receiverType = resolveReceiverType(target) ?: return null
        
        return when (selector.text) {
            "size" -> if (receiverType.isArrayOrPrimitiveArray || receiverType.isSubtypeOf(StandardClassIds.Collection)) target else null
            "length" -> if (receiverType.isSubtypeOf(StandardClassIds.CharSequence)) target else null
            else -> null
        }
    }
    
    private fun extractTargetExpression(type: RangeKtExpressionType, expression: KtExpression): KtExpression? {
        return when (type) {
            UNTIL, RANGE_UNTIL -> expression
            RANGE_TO -> extractFromRangeToExpression(expression)
            DOWN_TO -> null
        }
    }

    private fun extractFromRangeToExpression(expression: KtExpression): KtExpression? {
        if (expression !is KtBinaryExpression) return null
        if (expression.operationToken != KtTokens.MINUS) return null
        
        val leftOperand = expression.left ?: return null
        val rightOperand = expression.right ?: return null

        return if (rightOperand.isIntConstantExpression(1)) leftOperand else null
    }

    private fun KtExpression.isIntConstantExpression(value: Int): Boolean {
        return (this as? KtConstantExpression)?.text?.toIntOrNull() == value
    }

    private fun KaSession.resolveReceiverType(expression: KtExpression): KaType? {
        val resolvedCall = expression.resolveToCall()

        // 1. We're only interested in properties and not in function calls, hence "successfulVariableAccessCall"
        // 2. We're only interested in member properties, hence "dispatchReceiver" that gets the owning type
        
        return resolvedCall?.successfulVariableAccessCall()?.partiallyAppliedSymbol?.dispatchReceiver?.type
    }

    /**
     * Determines if we should suggest converting the index loop to an element loop.
     * This checks if the loop parameter is used exactly once as an array index with the same receiver.
     */
    private fun shouldSuggestElementLoop(range: RangeExpression, explicitReceiver: KtExpression?): Boolean {
        val forExpression = findContainingForLoop(range) ?: return false
        val loopUsage = findSingleLoopParameterUsage(forExpression) ?: return false

        return isValidElementLoopCandidate(loopUsage, explicitReceiver)
    }

    private fun findContainingForLoop(range: RangeExpression): KtForExpression? =
        range.expression.parents.match(KtContainerNode::class, last = KtForExpression::class)

    private fun findSingleLoopParameterUsage(forExpression: KtForExpression): KtArrayAccessExpression? {
        val loopParam = forExpression.loopParameter?.originalElement ?: return null
        val usage = ReferencesSearch.search(loopParam).asIterable().singleOrNull()?.element ?: return null
        return usage.parents.match(KtContainerNode::class, last = KtArrayAccessExpression::class)
    }

    private fun isValidElementLoopCandidate(arrayAccess: KtArrayAccessExpression, explicitReceiver: KtExpression?): Boolean {
        // Must use the loop parameter as the only index
        if (arrayAccess.indexExpressions.size != 1) return false

        // Check if array receiver matches the size call receiver
        val receiversMatch = if (explicitReceiver != null) {
            // Explicit receiver case: arr.size -> arr[i]
            (arrayAccess.arrayExpression as? KtSimpleNameExpression)?.reference?.resolve() ==
                    (explicitReceiver as? KtSimpleNameExpression)?.reference?.resolve()
        } else {
            // Implicit receiver case: size -> this[i]
            arrayAccess.arrayExpression is KtThisExpression
        }

        if (!receiversMatch) return false

        // Don't suggest if array access is being assigned to (it's being modified)
        val parent = arrayAccess.parent
        return parent !is KtBinaryExpression || parent.left != arrayAccess || parent.operationToken !in KtTokens.ALL_ASSIGNMENTS
    }

    override fun createQuickFix(
        range: RangeExpression,
        context: Context
    ): KotlinModCommandQuickFix<KtExpression> {
        return if (context.suggestElementLoop) ReplaceIndexLoopWithCollectionLoopQuickFix(context)
        else ReplaceManualRangeWithIndicesCallQuickFix(context)
    }

    /**
     * Creates an implicit 'this' expression based on the receiver info.
     * For unambiguous receivers, returns simple 'this'. For ambiguous ones, returns 'this@label'.
     */
    private fun createImplicitThis(project: Project, info: ImplicitReceiverInfo): KtThisExpression {
        val thisExpressionText = if (info.isUnambiguousLabel) "this" else "this@${info.receiverLabel?.render()}"
        return KtPsiFactory(project).createExpression(thisExpressionText) as KtThisExpression
    }

    private inner class ReplaceManualRangeWithIndicesCallQuickFix(
        private val context: Context
    ) : KotlinModCommandQuickFix<KtExpression>() {
        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("replace.manual.range.with.indices.call.quick.fix.text")

        override fun applyFix(project: Project, element: KtExpression, updater: ModPsiUpdater) {
            val secondArg = element.getArguments()?.second
            val receiver = when (secondArg) {
                is KtBinaryExpression -> (secondArg.left as? KtDotQualifiedExpression)?.receiverExpression
                is KtDotQualifiedExpression -> secondArg.receiverExpression
                else -> null
            }
            val newReceiver = when {
                receiver is KtThisExpression && receiver.labelQualifier == null -> null
                receiver != null -> receiver
                else -> context.implicitReceiverInfo?.takeUnless { it.isUnambiguousLabel }?.let { createImplicitThis(project, it) }
            }
            val psiFactory = KtPsiFactory(project)
            val newExpression = if (newReceiver != null) {
                psiFactory.createExpressionByPattern("$0.indices", newReceiver)
            } else {
                psiFactory.createExpression("indices")
            }
            val replaced: KtExpression = element.replaced(newExpression)
            replaced.removeUnnecessaryParentheses()
        }

        /**
         * Extracts the left and right arguments from various range expression types.
         * Supports binary expressions (a..b) and function calls (a.rangeTo(b)).
         */
        private fun KtExpression.getArguments(): Pair<KtExpression?, KtExpression?>? = when (this) {
            is KtBinaryExpression -> left to right

            is KtDotQualifiedExpression, is KtSafeQualifiedExpression -> {
                val qualified = this as KtQualifiedExpression
                val call = qualified.selectorExpression as? KtCallExpression
                val argument = call?.valueArguments?.singleOrNull()?.getArgumentExpression()
                qualified.receiverExpression to argument
            }

            is KtCallExpression -> {
                val argument = valueArguments.singleOrNull()?.getArgumentExpression()
                null to argument
            }

            else -> null
        }

        private fun KtExpression.removeUnnecessaryParentheses() {
            parents.takeWhile { it is KtParenthesizedExpression }.lastOrNull()?.let {
                if (it is KtParenthesizedExpression && KtPsiUtil.areParenthesesUseless(it)) {
                    it.replace(it.safeDeparenthesize())
                }
            }
        }
    }

    private inner class ReplaceIndexLoopWithCollectionLoopQuickFix(
        private val context: Context,
    ) : KotlinModCommandQuickFix<KtExpression>() {
        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("replace.index.loop.with.collection.loop.quick.fix.text")

        override fun applyFix(project: Project, element: KtExpression, updater: ModPsiUpdater) {
            val forExpression = element.getStrictParentOfType<KtForExpression>() ?: return
            val loopParameter = forExpression.loopParameter ?: return
            val loopRange = forExpression.loopRange ?: return

            val collection = resolveCollectionExpression(project)
            val usageInfo = LoopToCollectionTransformUtils.findSingleArrayAccessUsage(loopParameter) ?: return
            LoopToCollectionTransformUtils.transformLoop(project, usageInfo, loopParameter, loopRange, collection)
        }

        /**
         * Resolves the collection expression for the loop transformation.
         * Handles both explicit receivers (arr.size -> arr) and implicit ones (size -> this).
         */
        private fun resolveCollectionExpression(project: Project): KtExpression {
            // Use explicit receiver if available
            (context.sizeCall as? KtQualifiedExpression)?.receiverExpression?.let { return it }

            // For implicit receivers, only add explicit 'this@label' for ambiguous cases
            context.implicitReceiverInfo?.takeUnless { it.isUnambiguousLabel }?.let {
                return createImplicitThis(project, it)
            }

            // Default to simple 'this' for unambiguous cases
            return KtPsiFactory(project).createThisExpression()
        }
    }
}