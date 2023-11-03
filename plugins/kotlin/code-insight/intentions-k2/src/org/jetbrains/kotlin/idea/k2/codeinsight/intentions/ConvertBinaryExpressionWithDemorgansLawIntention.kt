// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AbstractKotlinModCommandWithContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AnalysisActionContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.utils.*
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression

internal class ConvertBinaryExpressionWithDemorgansLawIntention :
    AbstractKotlinModCommandWithContext<KtBinaryExpression, DemorgansLawContext>(KtBinaryExpression::class) {

    @Suppress("DialogTitleCapitalization")
    override fun getFamilyName(): String = KotlinBundle.message("demorgan.law")

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtBinaryExpression> = ApplicabilityRanges.SELF

    override fun apply(element: KtBinaryExpression, context: AnalysisActionContext<DemorgansLawContext>, updater: ModPsiUpdater) {
        val expr = element.topmostBinaryExpression()
        if (splitBooleanSequence(expr) == null) return
        applyDemorgansLaw(element, context.analyzeContext)
    }

    context(KtAnalysisSession)
    override fun prepareContext(element: KtBinaryExpression): DemorgansLawContext? {
        return prepareDemorgansLawContext(element)
    }

    override fun getActionName(element: KtBinaryExpression, context: DemorgansLawContext): String {
        val expression = element.topmostBinaryExpression()
        return when (expression.operationToken) {
            KtTokens.ANDAND -> KotlinBundle.message("replace.&&.with.||")
            KtTokens.OROR -> KotlinBundle.message("replace.||.with.&&")
            else -> throw IllegalArgumentException()
        }
    }

    override fun isApplicableByPsi(element: KtBinaryExpression): Boolean {
        val expression = element.topmostBinaryExpression()
        val operationToken = expression.operationToken
        if (operationToken != KtTokens.ANDAND && operationToken != KtTokens.OROR) return false
        return splitBooleanSequence(expression) != null
    }
}
