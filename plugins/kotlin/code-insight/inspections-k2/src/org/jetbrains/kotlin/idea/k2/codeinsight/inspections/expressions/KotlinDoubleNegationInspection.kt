// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.expressions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.AbstractKotlinApplicableInspection
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtPrefixExpression

internal class KotlinDoubleNegationInspection : AbstractKotlinApplicableInspection<KtPrefixExpression>(KtPrefixExpression::class) {
    override fun getFamilyName(): String = KotlinBundle.message("inspection.kotlin.double.negation.display.name")
    override fun getActionName(element: KtPrefixExpression): String =
        KotlinBundle.message("inspection.kotlin.double.negation.action.name")

    override fun isApplicableByPsi(element: KtPrefixExpression): Boolean =
        element.operationToken == KtTokens.EXCL
            && (element.parentThroughParenthesis as? KtPrefixExpression)?.operationToken == KtTokens.EXCL

    context(KtAnalysisSession)
    override fun isApplicableByAnalyze(element: KtPrefixExpression): Boolean = element.getKtType()?.isBoolean == true

    override fun apply(element: KtPrefixExpression, project: Project, editor: Editor?) {
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
