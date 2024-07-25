// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtEscapeStringTemplateEntry
import org.jetbrains.kotlin.psi.KtPsiFactory

class ConvertIllegalEscapeToUnicodeEscapeFix(
    element: KtElement,
    private val unicodeEscape: String
) : KotlinPsiUpdateModCommandAction.ElementBased<KtElement, Unit>(element, Unit) {

    override fun getFamilyName(): String = KotlinBundle.message("convert.to.unicode.escape")

    override fun invoke(
        actionContext: ActionContext,
        element: KtElement,
        elementContext: Unit,
        updater: ModPsiUpdater,
    ) {
        val psiFactory = KtPsiFactory(actionContext.project)
        when (element) {
            is KtConstantExpression -> element.replace(psiFactory.createExpression("'$unicodeEscape'"))
            is KtEscapeStringTemplateEntry -> element.replace(psiFactory.createStringTemplate(unicodeEscape).entries.first())
        }
    }
}
