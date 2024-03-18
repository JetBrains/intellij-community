// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.builtins.StandardNames.KOTLIN_REFLECT_FQ_NAME
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AbstractKotlinModCommandWithContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AnalysisActionContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.k2.refactoring.util.ConvertReferenceToLambdaUtil
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression

internal class ConvertReferenceToLambdaIntention: AbstractKotlinModCommandWithContext<KtCallableReferenceExpression, String>(
    KtCallableReferenceExpression::class
) {

    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("convert.reference.to.lambda")

    override fun getActionName(element: KtCallableReferenceExpression, context: String): @IntentionName String = familyName

    context(KtAnalysisSession)
    override fun prepareContext(element: KtCallableReferenceExpression): String? {
        return ConvertReferenceToLambdaUtil.prepareLambdaExpressionText(element)
    }

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtCallableReferenceExpression> {
        return ApplicabilityRanges.SELF
    }

    override fun isApplicableByPsi(element: KtCallableReferenceExpression): Boolean {
        return true
    }

    context(KtAnalysisSession)
    override fun isApplicableByAnalyze(element: KtCallableReferenceExpression): Boolean {
        val expectedType = element.getExpectedType() ?: return true
        val classId = (expectedType as? KtNonErrorClassType)?.classId ?: return true
        val packageFqName = classId.packageFqName
        return packageFqName.isRoot || packageFqName != KOTLIN_REFLECT_FQ_NAME
    }

    override fun apply(
        element: KtCallableReferenceExpression,
        context: AnalysisActionContext<String>,
        updater: ModPsiUpdater
    ) {
        ConvertReferenceToLambdaUtil.convertReferenceToLambdaExpression(element, context.analyzeContext)
    }
}