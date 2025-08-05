// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared.collections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.isDefinitelyNotNull
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.utils.callExpression
import org.jetbrains.kotlin.idea.codeinsights.impl.base.asQuickFix
import org.jetbrains.kotlin.idea.quickfix.ReplaceWithDotCallFix
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

// TODO: This class is currently only registered for K2 due to bugs in the
//  K1 implementation of the analysis API.
//  Once it is fixed, it should be used for both K1 and K2.
//  See: KT-65376
class UselessCallOnNotNullInspection : AbstractUselessCallInspection() {
    override val uselessFqNames: Map<CallableId, Conversion> = mapOf(
        topLevelCallableId("kotlin.collections", "orEmpty") to Conversion.Delete,
        topLevelCallableId("kotlin.sequences", "orEmpty") to Conversion.Delete,
        topLevelCallableId("kotlin.text", "orEmpty") to Conversion.Delete,
        topLevelCallableId("kotlin.text", "isNullOrEmpty") to Conversion.Replace("isEmpty"),
        topLevelCallableId("kotlin.text", "isNullOrBlank") to Conversion.Replace("isBlank"),
        topLevelCallableId("kotlin.collections", "isNullOrEmpty") to Conversion.Replace("isEmpty")
    )

    override val uselessNames = uselessFqNames.keys.toShortNames()

    context(_: KaSession)
    override fun QualifiedExpressionVisitor.suggestConversionIfNeeded(
        expression: KtQualifiedExpression,
        calleeExpression: KtExpression,
        conversion: Conversion
    ) {
        val newName = (conversion as? Conversion.Replace)?.replacementName

        val safeExpression = expression as? KtSafeQualifiedExpression
        val notNullType = expression.receiverExpression.isDefinitelyNotNull
        val defaultRange =
            TextRange(expression.operationTokenNode.startOffset, calleeExpression.endOffset).shiftRight(-expression.startOffset)
        if (newName != null && (notNullType || safeExpression != null)) {
            val fixes = listOfNotNull(
                createRenameUselessCallFix(expression, newName),
                safeExpression?.let { LocalQuickFix.from(ReplaceWithDotCallFix(safeExpression)) }
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
                ReplaceWithDotCallFix(safeExpression).asQuickFix(),
            )
        }
    }

    context(_: KaSession)
    private fun createRenameUselessCallFix(
        expression: KtQualifiedExpression,
        newFunctionName: String
    ): RenameUselessCallFix? {
        if (expression.isUsingLabelInScope(newFunctionName)) {
            return null
        }
        val nonInvertedFix = RenameUselessCallFix(newFunctionName, invert = false)
        if (expression.parent.safeAs<KtPrefixExpression>()?.operationToken != KtTokens.EXCL) {
            return nonInvertedFix
        }
        val copiedExpression = expression.copied().apply {
            callExpression?.calleeExpression?.replace(KtPsiFactory(expression.project).createExpression(newFunctionName))
        }
        val codeFragment = KtPsiFactory(expression.project).createExpressionCodeFragment(copiedExpression.text, expression)
        val contentElement = codeFragment.getContentElement() as? KtQualifiedExpression ?: return nonInvertedFix
        // After changing to the inverted name, we make sure that if the function is inverted, we are calling the correct function.
        // (Relevant if, for example, a different List.isEmpty() is already defined in the same scope, we do not want to use it)
        val invertedName = contentElement.invertSelectorFunction()?.callExpression?.calleeExpression?.text
        return if (invertedName != null && !expression.isUsingLabelInScope(invertedName)) {
            RenameUselessCallFix(invertedName, invert = true)
        } else {
            nonInvertedFix
        }
    }
}