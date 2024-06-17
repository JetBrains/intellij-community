// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.refactoring.util.getExplicitLambdaSignature
import org.jetbrains.kotlin.idea.refactoring.util.specifyExplicitLambdaSignature
import org.jetbrains.kotlin.psi.KtLambdaExpression

internal class SpecifyExplicitLambdaSignatureIntention :
    KotlinApplicableModCommandAction<KtLambdaExpression, String>(KtLambdaExpression::class) {

    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("specify.explicit.lambda.signature")

    override fun isApplicableByPsi(element: KtLambdaExpression): Boolean {
        return element.functionLiteral.arrow == null || !element.valueParameters.all { it.typeReference != null }
    }

    context(KaSession)
    override fun prepareContext(element: KtLambdaExpression): String? {
        return getExplicitLambdaSignature(element)
    }

    override fun invoke(
      actionContext: ActionContext,
      element: KtLambdaExpression,
      elementContext: String,
      updater: ModPsiUpdater,
    ) {
        specifyExplicitLambdaSignature(element, elementContext)
    }
}