// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.psi.isInsideAnnotationEntryArgumentList
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.convertStringTemplateToBuildStringCall
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.isSingleQuoted

internal class ConvertStringTemplateToBuildStringIntention :
    KotlinApplicableModCommandAction<KtStringTemplateExpression, Unit>(KtStringTemplateExpression::class),
    LowPriorityAction {

    override fun getFamilyName(): String = KotlinBundle.message("convert.string.template.to.build.string")

    override fun isApplicableByPsi(element: KtStringTemplateExpression): Boolean =
        element.isSingleQuoted() && !element.isInsideAnnotationEntryArgumentList() && element.interpolationPrefix == null

    context(KaSession)
    override fun prepareContext(element: KtStringTemplateExpression) {
    }

    override fun invoke(
      actionContext: ActionContext,
      element: KtStringTemplateExpression,
      elementContext: Unit,
      updater: ModPsiUpdater,
    ) {
        val buildStringCall = convertStringTemplateToBuildStringCall(element)
        shortenReferences(buildStringCall)
    }
}