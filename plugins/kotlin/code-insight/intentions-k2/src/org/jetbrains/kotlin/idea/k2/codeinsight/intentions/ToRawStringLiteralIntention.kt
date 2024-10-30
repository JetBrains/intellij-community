// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsights.impl.base.canBeConvertedToStringLiteral
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.convertToStringLiteral
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

internal class ToRawStringLiteralIntention :
    KotlinApplicableModCommandAction<KtStringTemplateExpression, Unit>(KtStringTemplateExpression::class) {

    override fun getFamilyName(): String = KotlinBundle.message("convert.to.raw.string.literal")
    override fun getPresentation(context: ActionContext, element: KtStringTemplateExpression): Presentation =
        Presentation.of(familyName).withPriority(PriorityAction.Priority.LOW)

    context(KaSession)
    override fun prepareContext(element: KtStringTemplateExpression) {
    }

    override fun invoke(
      actionContext: ActionContext,
      element: KtStringTemplateExpression,
      elementContext: Unit,
      updater: ModPsiUpdater,
    ) {
        convertToStringLiteral(element, actionContext, updater)
    }

    override fun isApplicableByPsi(element: KtStringTemplateExpression): Boolean = element.canBeConvertedToStringLiteral()
}