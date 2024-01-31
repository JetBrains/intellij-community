// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared.collections

import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyzeCopy
import org.jetbrains.kotlin.analysis.project.structure.DanglingFileResolutionMode
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.utils.callExpression
import org.jetbrains.kotlin.idea.quickfix.ReplaceWithDotCallFix
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPrefixExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtSafeQualifiedExpression
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class UselessCallOnNotNullInspection : AbstractUselessCallInspection() {
    override val uselessFqNames = mapOf(
        "kotlin.collections.orEmpty" to Conversion.Delete,
        "kotlin.sequences.orEmpty" to Conversion.Delete,
        "kotlin.text.orEmpty" to Conversion.Delete,
        "kotlin.text.isNullOrEmpty" to Conversion.Replace("isEmpty"),
        "kotlin.text.isNullOrBlank" to Conversion.Replace("isBlank"),
        "kotlin.collections.isNullOrEmpty" to Conversion.Replace("isEmpty")
    )

    override val uselessNames = uselessFqNames.keys.toShortNames()

    context(KtAnalysisSession)
    override fun QualifiedExpressionVisitor.suggestConversionIfNeeded(
        expression: KtQualifiedExpression,
        calleeExpression: KtExpression,
        conversion: Conversion
    ) {
        val newName = (conversion as? Conversion.Replace)?.replacementName

        val safeExpression = expression as? KtSafeQualifiedExpression
        val notNullType = expression.receiverExpression.isDefinitelyNotNull()
        val defaultRange =
            TextRange(expression.operationTokenNode.startOffset, calleeExpression.endOffset).shiftRight(-expression.startOffset)
        if (newName != null && (notNullType || safeExpression != null)) {
            val fixes = listOfNotNull(
                createRenameUselessCallFix(expression, newName),
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

    context(KtAnalysisSession)
    private fun createRenameUselessCallFix(
        expression: KtQualifiedExpression,
        newFunctionName: String
    ): RenameUselessCallFix {
        val nonInvertedFix = RenameUselessCallFix(newFunctionName, invert = false)
        if (expression.parent.safeAs<KtPrefixExpression>()?.operationToken != KtTokens.EXCL) {
            return nonInvertedFix
        }
        // Here we create a copy of the file and attempt to change the expression to the inverted function
        val copiedFile = expression.containingFile.copy() as? PsiFile ?: return nonInvertedFix
        val copiedExpression = PsiTreeUtil.findSameElementInCopy(expression, copiedFile) ?: return nonInvertedFix
        val changedCopy = copiedExpression.apply {
            callExpression?.calleeExpression?.replace(KtPsiFactory(expression.project).createExpression(newFunctionName))
        }
        return analyzeCopy(changedCopy, DanglingFileResolutionMode.PREFER_SELF) {
            // After changing to the inverted name, we make sure that if the function is inverted, we are calling the correct function.
            // (Relevant if for example a different List.isEmpty() is already defined in the same scope, we do not want to use it)
            val invertedName = changedCopy.invertSelectorFunction()?.callExpression?.calleeExpression?.text
            if (invertedName != null) {
                RenameUselessCallFix(invertedName, invert = true)
            } else {
                nonInvertedFix
            }
        }
    }
}