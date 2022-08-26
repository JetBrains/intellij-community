// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.elementType
import org.jetbrains.kotlin.idea.base.psi.safeDeparenthesize
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.*
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.psi.stubs.elements.KtStubElementTypes

class RedundantElvisReturnNullInspection :
    AbstractKotlinApplicatorBasedInspection<KtBinaryExpression, RedundantElvisReturnNullInspection.RedundantElvisReturnNullInspectionInput>(
        KtBinaryExpression::class
    ) {
    class RedundantElvisReturnNullInspectionInput : KotlinApplicatorInput

    override fun getApplicabilityRange() = applicabilityRanges { binaryExpression: KtBinaryExpression ->
        val right =
            binaryExpression.right?.safeDeparenthesize()?.takeIf { it == binaryExpression.right }
                ?: return@applicabilityRanges emptyList()
        listOf(TextRange(binaryExpression.operationReference.startOffset, right.endOffset).shiftLeft(binaryExpression.startOffset))
    }

    override fun getInputProvider() = inputProvider { binaryExpression: KtBinaryExpression ->
        // Returns null if LHS of the binary expression is not nullable.
        if (binaryExpression.left?.getKtType()?.isMarkedNullable != true) return@inputProvider null
        return@inputProvider RedundantElvisReturnNullInspectionInput()
    }

    override fun getApplicator() =
        applicator<KtBinaryExpression, RedundantElvisReturnNullInspection.RedundantElvisReturnNullInspectionInput> {
            familyName(KotlinBundle.lazyMessage(("remove.redundant.elvis.return.null.text")))
            actionName(KotlinBundle.lazyMessage(("inspection.redundant.elvis.return.null.descriptor")))
            isApplicableByPsi { binaryExpression ->
                // The binary expression must be in a form of "return <left expression> ?: return null".
                val returnExpression = binaryExpression.right as? KtReturnExpression ?: return@isApplicableByPsi false

                // Returns false if RHS is not "return null".
                val deparenthesizedReturnExpression =
                    returnExpression.returnedExpression?.safeDeparenthesize() ?: return@isApplicableByPsi false
                if (deparenthesizedReturnExpression.elementType != KtStubElementTypes.NULL) return@isApplicableByPsi false

                val isTargetOfReturn =
                    binaryExpression == binaryExpression.getStrictParentOfType<KtReturnExpression>()?.returnedExpression?.safeDeparenthesize()
                isTargetOfReturn && binaryExpression.operationToken == KtTokens.ELVIS
            }
            applyTo { binaryExpression, _ ->
                val left = binaryExpression.left ?: return@applyTo
                binaryExpression.replace(left)
            }
        }
}