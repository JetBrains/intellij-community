// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.expressions

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.asUnit
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtPrefixExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

internal class KotlinDoubleNegationInspection : KotlinApplicableInspectionBase.Simple<KtPrefixExpression, Unit>() {

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ) = object : KtVisitorVoid() {

        override fun visitPrefixExpression(expression: KtPrefixExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }
    }

    override fun getProblemDescription(
        element: KtPrefixExpression,
        context: Unit,
    ): String = KotlinBundle.message("inspection.kotlin.double.negation.display.name")

    override fun isApplicableByPsi(element: KtPrefixExpression): Boolean =
        element.operationToken == KtTokens.EXCL
                && (element.parentThroughParenthesis as? KtPrefixExpression)?.operationToken == KtTokens.EXCL

    override fun KaSession.prepareContext(element: KtPrefixExpression): Unit? =
        element.expressionType
            ?.isBooleanType
            ?.asUnit

    override fun createQuickFixes(
        element: KtPrefixExpression,
        context: Unit,
    ): Array<KotlinModCommandQuickFix<KtPrefixExpression>> = arrayOf(object : KotlinModCommandQuickFix<KtPrefixExpression>() {

        override fun getFamilyName(): String =
            KotlinBundle.message("inspection.kotlin.double.negation.action.name")

        override fun applyFix(
            project: Project,
            element: KtPrefixExpression,
            updater: ModPsiUpdater,
        ) {
            element.baseExpression?.let { element.parentThroughParenthesis.replace(it) }
        }

    })
}

private val PsiElement.parentThroughParenthesis: PsiElement
    get() {
        var result = parent
        while (result is KtParenthesizedExpression) {
            result = result.parent
        }
        return result
    }
