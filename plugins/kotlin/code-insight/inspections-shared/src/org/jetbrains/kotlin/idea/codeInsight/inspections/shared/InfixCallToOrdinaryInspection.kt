// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.convertInfixCallToOrdinary
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.binaryExpressionVisitor

internal class InfixCallToOrdinaryInspection : KotlinApplicableInspectionBase.Simple<KtBinaryExpression, Unit>() {

    override fun getProblemDescription(
        element: KtBinaryExpression,
        context: Unit,
    ) = KotlinBundle.message("replace.infix.call.with.ordinary.call")

    override fun createQuickFix(
        element: KtBinaryExpression,
        context: Unit,
    ): KotlinModCommandQuickFix<KtBinaryExpression> = object : KotlinModCommandQuickFix<KtBinaryExpression>() {

        override fun applyFix(
            project: Project,
            element: KtBinaryExpression,
            updater: ModPsiUpdater,
        ) {
            convertInfixCallToOrdinary(element)
        }

        override fun getFamilyName(): String =
            KotlinBundle.message("replace.infix.call.with.ordinary.call")
    }

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ) = binaryExpressionVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    override fun getApplicableRanges(element: KtBinaryExpression): List<TextRange> =
        listOf(element.operationReference.textRangeInParent)

    override fun isApplicableByPsi(element: KtBinaryExpression): Boolean {
        return !(element.operationToken != KtTokens.IDENTIFIER || element.left == null || element.right == null)
    }

    override fun KaSession.prepareContext(element: KtBinaryExpression) {
    }
}