// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.expressions

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.AbstractKotlinApplicableInspection
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtPrefixExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

internal class KotlinDoubleNegationInspection : AbstractKotlinApplicableInspection<KtPrefixExpression>() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
        return object : KtVisitorVoid() {
            override fun visitPrefixExpression(expression: KtPrefixExpression) {
                visitTargetElement(expression, holder, isOnTheFly)
            }
        }
    }
    override fun getProblemDescription(element: KtPrefixExpression): String =
        KotlinBundle.message("inspection.kotlin.double.negation.display.name")

    override fun getActionFamilyName(): String = KotlinBundle.message("inspection.kotlin.double.negation.action.name")

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtPrefixExpression> = ApplicabilityRanges.SELF

    override fun isApplicableByPsi(element: KtPrefixExpression): Boolean =
        element.operationToken == KtTokens.EXCL
            && (element.parentThroughParenthesis as? KtPrefixExpression)?.operationToken == KtTokens.EXCL

    context(KtAnalysisSession)
    override fun isApplicableByAnalyze(element: KtPrefixExpression): Boolean = element.getKtType()?.isBoolean == true

    override fun apply(element: KtPrefixExpression, project: Project, updater: ModPsiUpdater) {
        element.baseExpression?.let { element.parentThroughParenthesis.replace(it) }
    }
}

private val PsiElement.parentThroughParenthesis: PsiElement
    get() {
        var result = parent
        while (result is KtParenthesizedExpression) {
            result = result.parent
        }
        return result
    }
