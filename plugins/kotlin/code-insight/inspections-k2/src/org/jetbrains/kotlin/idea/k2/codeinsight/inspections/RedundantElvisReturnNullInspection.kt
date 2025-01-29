// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.elementType
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.psi.safeDeparenthesize
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.asUnit
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.psi.stubs.elements.KtStubElementTypes

internal class RedundantElvisReturnNullInspection : KotlinApplicableInspectionBase.Simple<KtBinaryExpression, Unit>() {

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ) = object : KtVisitorVoid() {

        override fun visitBinaryExpression(expression: KtBinaryExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }
    }

    override fun getProblemDescription(
        element: KtBinaryExpression,
        context: Unit,
    ): String = KotlinBundle.message("inspection.redundant.elvis.return.null.descriptor")

    override fun getApplicableRanges(element: KtBinaryExpression): List<TextRange> {
        val right = element.right
            ?.safeDeparenthesize()
            ?.takeIf { it == element.right }
            ?: return emptyList()

        val textRange = TextRange(element.operationReference.startOffset, right.endOffset)
            .shiftLeft(element.startOffset)
        return listOf(textRange)
    }

    override fun isApplicableByPsi(element: KtBinaryExpression): Boolean {
        // The binary expression must be in a form of `return <left expression> ?: return null`.
        val returnExpression = element.right as? KtReturnExpression ?: return false
        val returnedExpression = returnExpression.returnedExpression?.safeDeparenthesize() ?: return false
        if (returnedExpression.elementType != KtStubElementTypes.NULL) return false

        val isTargetOfReturn = element == element.getStrictParentOfType<KtReturnExpression>()?.returnedExpression?.safeDeparenthesize()
        return isTargetOfReturn && element.operationToken == KtTokens.ELVIS
    }

    // The LHS of the binary expression must be nullable.
    context(KaSession)
    override fun prepareContext(element: KtBinaryExpression): Unit? =
        element.left
            ?.expressionType
            ?.isMarkedNullable
            ?.asUnit

    override fun createQuickFixes(
        element: KtBinaryExpression,
        context: Unit,
    ): Array<KotlinModCommandQuickFix<KtBinaryExpression>> = arrayOf(object : KotlinModCommandQuickFix<KtBinaryExpression>() {

        override fun getFamilyName(): String =
            KotlinBundle.message("remove.redundant.elvis.return.null.text")

        override fun applyFix(
            project: Project,
            element: KtBinaryExpression,
            updater: ModPsiUpdater,
        ) {
            val left = element.left ?: return
            element.replace(left)
        }
    })
}