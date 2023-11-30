// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AbstractKotlinApplicableModCommandIntentionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.codeinsights.impl.base.canBeConvertedToStringLiteral
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.convertToStringLiteral
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

internal class ToRawStringLiteralIntention : AbstractKotlinApplicableModCommandIntentionBase<KtStringTemplateExpression>(
    KtStringTemplateExpression::class
), LowPriorityAction {
    override fun getFamilyName(): String = KotlinBundle.message("convert.to.raw.string.literal")

    override fun getActionName(element: KtStringTemplateExpression): String = familyName

    override fun invoke(context: ActionContext, element: KtStringTemplateExpression, updater: ModPsiUpdater) {
        convertToStringLiteral(element, context, updater)
    }

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtStringTemplateExpression> = ApplicabilityRanges.SELF

    override fun isApplicableByPsi(element: KtStringTemplateExpression): Boolean = element.canBeConvertedToStringLiteral()
}