// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections.collections

import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyzeAsReplacement
import org.jetbrains.kotlin.idea.caches.resolve.findModuleDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.inspections.ReplaceNegatedIsEmptyWithIsNotEmptyInspection.Companion.invertSelectorFunction
import org.jetbrains.kotlin.idea.intentions.callExpression
import org.jetbrains.kotlin.idea.quickfix.ReplaceWithDotCallFix
import org.jetbrains.kotlin.idea.resolve.dataFlowValueFactory
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.bindingContextUtil.getDataFlowInfoBefore
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class UselessCallOnNotNullInspection : AbstractUselessCallInspection() {
    override val uselessFqNames = mapOf(
        "kotlin.collections.orEmpty" to deleteConversion,
        "kotlin.sequences.orEmpty" to deleteConversion,
        "kotlin.text.orEmpty" to deleteConversion,
        "kotlin.text.isNullOrEmpty" to Conversion("isEmpty"),
        "kotlin.text.isNullOrBlank" to Conversion("isBlank"),
        "kotlin.collections.isNullOrEmpty" to Conversion("isEmpty")
    )

    override val uselessNames = uselessFqNames.keys.toShortNames()

    override fun QualifiedExpressionVisitor.suggestConversionIfNeeded(
        expression: KtQualifiedExpression,
        calleeExpression: KtExpression,
        context: BindingContext,
        conversion: Conversion
    ) {
        val newName = conversion.replacementName

        val safeExpression = expression as? KtSafeQualifiedExpression
        val notNullType = expression.receiverExpression.isNotNullType(context)
        val defaultRange =
            TextRange(expression.operationTokenNode.startOffset, calleeExpression.endOffset).shiftRight(-expression.startOffset)
        if (newName != null && (notNullType || safeExpression != null)) {
            val fixes = listOfNotNull(
                createRenameUselessCallFix(expression, newName, context),
                safeExpression?.let { IntentionWrapper(ReplaceWithDotCallFix(safeExpression)) }
            )
            val descriptor = holder.manager.createProblemDescriptor(
                expression,
                defaultRange,
                KotlinBundle.message("call.on.not.null.type.may.be.reduced"),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                isOnTheFly,
                *fixes.toTypedArray()
            )
            holder.registerProblem(descriptor)
        } else if (notNullType) {
            val descriptor = holder.manager.createProblemDescriptor(
                expression,
                defaultRange,
                KotlinBundle.message("useless.call.on.not.null.type"),
                ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                isOnTheFly,
                RemoveUselessCallFix()
            )
            holder.registerProblem(descriptor)
        } else if (safeExpression != null) {
            holder.registerProblem(
                safeExpression.operationTokenNode.psi,
                KotlinBundle.message("this.call.is.useless.with"),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                IntentionWrapper(ReplaceWithDotCallFix(safeExpression))
            )
        }
    }

    private fun KtExpression.isNotNullType(context: BindingContext): Boolean {
        val type = getType(context) ?: return false
        val dataFlowValueFactory = getResolutionFacade().dataFlowValueFactory
        val dataFlowValue = dataFlowValueFactory.createDataFlowValue(this, type, context, findModuleDescriptor())
        val stableNullability = context.getDataFlowInfoBefore(this).getStableNullability(dataFlowValue)
        return !stableNullability.canBeNull()
    }

    private fun createRenameUselessCallFix(
        expression: KtQualifiedExpression,
        newFunctionName: String,
        context: BindingContext
    ): RenameUselessCallFix {
        if (expression.parent.safeAs<KtPrefixExpression>()?.operationToken != KtTokens.EXCL) {
            return RenameUselessCallFix(newFunctionName, invert = false)
        }

        val copy = expression.copy().safeAs<KtQualifiedExpression>()?.apply {
            callExpression?.calleeExpression?.replace(KtPsiFactory(expression.project).createExpression(newFunctionName))
        }
        val newContext = copy?.analyzeAsReplacement(expression, context)
        val invertedName = copy?.invertSelectorFunction(newContext)?.callExpression?.calleeExpression?.text
        return if (invertedName != null) {
            RenameUselessCallFix(invertedName, invert = true)
        } else {
            RenameUselessCallFix(newFunctionName, invert = false)
        }
    }
}

