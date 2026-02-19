// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.branchedTransformations.convertIfToWhen
import org.jetbrains.kotlin.psi.KtIfExpression

internal class IfToWhenIntention : KotlinApplicableModCommandAction.Simple<KtIfExpression>(KtIfExpression::class) {
    override fun getFamilyName(): String = KotlinBundle.message("replace.if.with.when")

    override fun invoke(
        actionContext: ActionContext,
        element: KtIfExpression,
        elementContext: Unit,
        updater: ModPsiUpdater,
    ) {
        convertIfToWhen(element, updater)
    }

    override fun getApplicableRanges(element: KtIfExpression): List<TextRange> =
        ApplicabilityRanges.ifKeyword(element)

    override fun isApplicableByPsi(element: KtIfExpression): Boolean =
        element.then != null
}
