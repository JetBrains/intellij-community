// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.psi.isInsideAnnotationEntryArgumentList
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.convertStringTemplateToBuildStringCall
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.isSingleQuoted

internal class ConvertStringTemplateToBuildStringIntention :
    KotlinApplicableModCommandAction.Simple<KtStringTemplateExpression>(KtStringTemplateExpression::class) {

    override fun getFamilyName(): String = KotlinBundle.message("convert.string.template.to.build.string")

    override fun getPresentation(context: ActionContext, element: KtStringTemplateExpression): Presentation =
        Presentation.of(familyName).withPriority(PriorityAction.Priority.LOW)

    override fun isApplicableByPsi(element: KtStringTemplateExpression): Boolean =
        element.isSingleQuoted() && !element.isInsideAnnotationEntryArgumentList()

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