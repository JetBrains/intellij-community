// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.elementType
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.base.psi.safeDeparenthesize
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.AbstractKotlinApplicableInspection
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.applicabilityRanges
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.psi.stubs.elements.KtStubElementTypes

internal class RedundantElvisReturnNullInspection : AbstractKotlinApplicableInspection<KtBinaryExpression>() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
        return object : KtVisitorVoid() {
            override fun visitBinaryExpression(expression: KtBinaryExpression) {
                visitTargetElement(expression, holder, isOnTheFly)
            }
        }
    }
    override fun getProblemDescription(element: KtBinaryExpression): String = KotlinBundle.message("inspection.redundant.elvis.return.null.descriptor")
    override fun getActionFamilyName(): String = KotlinBundle.message("remove.redundant.elvis.return.null.text")

    override fun getApplicabilityRange() = applicabilityRanges { binaryExpression: KtBinaryExpression ->
        val right =
            binaryExpression.right?.safeDeparenthesize()?.takeIf { it == binaryExpression.right }
                ?: return@applicabilityRanges emptyList()
        listOf(TextRange(binaryExpression.operationReference.startOffset, right.endOffset).shiftLeft(binaryExpression.startOffset))
    }

    override fun isApplicableByPsi(element: KtBinaryExpression): Boolean {
        // The binary expression must be in a form of `return <left expression> ?: return null`.
        val returnExpression = element.right as? KtReturnExpression ?: return false
        val returnedExpression = returnExpression.returnedExpression?.safeDeparenthesize() ?: return false
        if (returnedExpression.elementType != KtStubElementTypes.NULL) return false

        val isTargetOfReturn = element == element.getStrictParentOfType<KtReturnExpression>()?.returnedExpression?.safeDeparenthesize()
        return isTargetOfReturn && element.operationToken == KtTokens.ELVIS
    }

    context(KtAnalysisSession)
    override fun isApplicableByAnalyze(element: KtBinaryExpression): Boolean {
        // The LHS of the binary expression must be nullable.
        return element.left?.getKtType()?.isMarkedNullable == true
    }

    override fun apply(element: KtBinaryExpression, project: Project, updater: ModPsiUpdater) {
        val left = element.left ?: return
        element.replace(left)
    }
}