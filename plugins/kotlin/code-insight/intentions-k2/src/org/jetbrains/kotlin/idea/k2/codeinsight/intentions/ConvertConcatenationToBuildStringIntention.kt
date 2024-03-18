// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.psi.isInsideAnnotationEntryArgumentList
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AbstractKotlinApplicableModCommandIntention
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.convertConcatenationToBuildStringCall
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression

internal class ConvertConcatenationToBuildStringIntention : AbstractKotlinApplicableModCommandIntention<KtBinaryExpression>(KtBinaryExpression::class) {
    override fun getFamilyName(): String = KotlinBundle.message("convert.concatenation.to.build.string")
    override fun getActionName(element: KtBinaryExpression): String = familyName

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtBinaryExpression> = ApplicabilityRanges.SELF

    override fun isApplicableByPsi(element: KtBinaryExpression): Boolean =
        element.operationToken == KtTokens.PLUS && !element.isInsideAnnotationEntryArgumentList()

    context(KtAnalysisSession)
    override fun isApplicableByAnalyze(element: KtBinaryExpression): Boolean {
        val parent = element.parent
        return element.getKtType()?.isString == true && (
                parent !is KtBinaryExpression ||
                parent.operationToken != KtTokens.PLUS ||
                parent.getKtType()?.isString == false)
    }

    override fun apply(element: KtBinaryExpression, context: ActionContext, updater: ModPsiUpdater) {
        val buildStringCall = convertConcatenationToBuildStringCall(element)
        shortenReferences(buildStringCall)
    }
}