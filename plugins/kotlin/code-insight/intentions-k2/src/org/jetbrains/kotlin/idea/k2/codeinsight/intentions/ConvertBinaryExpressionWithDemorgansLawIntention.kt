// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AbstractKotlinModCommandWithContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AnalysisActionContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.utils.DemorgansLawUtils
import org.jetbrains.kotlin.idea.codeinsight.utils.DemorgansLawUtils.applyDemorgansLaw
import org.jetbrains.kotlin.idea.codeinsight.utils.DemorgansLawUtils.getOperandsIfAllBoolean
import org.jetbrains.kotlin.idea.codeinsight.utils.DemorgansLawUtils.splitBooleanSequence
import org.jetbrains.kotlin.idea.codeinsight.utils.DemorgansLawUtils.topmostBinaryExpression
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression

internal class ConvertBinaryExpressionWithDemorgansLawIntention :
    AbstractKotlinModCommandWithContext<KtBinaryExpression, DemorgansLawUtils.Context>(KtBinaryExpression::class) {

    @Suppress("DialogTitleCapitalization")
    override fun getFamilyName(): String = KotlinBundle.message("demorgan.law")

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtBinaryExpression> = ApplicabilityRanges.SELF

    override fun apply(element: KtBinaryExpression, context: AnalysisActionContext<DemorgansLawUtils.Context>, updater: ModPsiUpdater) {
        val topmostBinaryExpression = element.topmostBinaryExpression()
        if (splitBooleanSequence(topmostBinaryExpression) == null) return
        applyDemorgansLaw(topmostBinaryExpression, context.analyzeContext)
    }

    context(KtAnalysisSession)
    override fun prepareContext(element: KtBinaryExpression): DemorgansLawUtils.Context? {
        val operands = getOperandsIfAllBoolean(element) ?: return null
        return DemorgansLawUtils.prepareContext(operands)
    }

    override fun getActionName(element: KtBinaryExpression, context: DemorgansLawUtils.Context): String {
        val topmostBinaryExpression = element.topmostBinaryExpression()
        return when (topmostBinaryExpression.operationToken) {
            KtTokens.ANDAND -> KotlinBundle.message("replace.&&.with.||")
            KtTokens.OROR -> KotlinBundle.message("replace.||.with.&&")
            else -> throw IllegalArgumentException()
        }
    }

    override fun isApplicableByPsi(element: KtBinaryExpression): Boolean {
        val topmostBinaryExpression = element.topmostBinaryExpression()
        val operationToken = topmostBinaryExpression.operationToken
        if (operationToken != KtTokens.ANDAND && operationToken != KtTokens.OROR) return false
        return splitBooleanSequence(topmostBinaryExpression) != null
    }
}
