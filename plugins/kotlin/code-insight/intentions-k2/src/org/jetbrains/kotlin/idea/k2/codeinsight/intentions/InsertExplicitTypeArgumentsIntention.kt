// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.utils.addTypeArguments
import org.jetbrains.kotlin.idea.codeinsight.utils.getRenderedTypeArguments
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.psi.KtCallExpression

internal class InsertExplicitTypeArgumentsIntention :
    KotlinApplicableModCommandAction<KtCallExpression, String>(KtCallExpression::class) {

    override fun getApplicableRanges(element: KtCallExpression): List<TextRange> =
        ApplicabilityRanges.calleeExpression(element)

    override fun isApplicableByPsi(element: KtCallExpression): Boolean = element.typeArguments.isEmpty() && element.calleeExpression != null

    override fun getFamilyName(): String = KotlinBundle.message("add.explicit.type.arguments")

    override fun KaSession.prepareContext(element: KtCallExpression): String? = getRenderedTypeArguments(element)

    override fun invoke(
      actionContext: ActionContext,
      element: KtCallExpression,
      elementContext: String,
      updater: ModPsiUpdater,
    ) {
        addTypeArguments(element, elementContext, actionContext.project)
    }
}
