// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtEscapeStringTemplateEntry
import org.jetbrains.kotlin.psi.KtPsiFactory

class ConvertIllegalEscapeToUnicodeEscapeFix private constructor(
    element: KtElement,
    private val unicodeEscape: String
) : PsiUpdateModCommandAction<KtElement>(element) {

    override fun getFamilyName(): String = KotlinBundle.message("convert.to.unicode.escape")

    override fun invoke(
        actionContext: ActionContext,
        element: KtElement,
        updater: ModPsiUpdater,
    ) {
        val psiFactory = KtPsiFactory(actionContext.project)
        when (element) {
            is KtConstantExpression -> element.replace(psiFactory.createExpression("'$unicodeEscape'"))
            is KtEscapeStringTemplateEntry -> element.replace(psiFactory.createStringTemplate(unicodeEscape).entries.first())
        }
    }

    companion object {
        fun createIfApplicable(element: KtElement): ConvertIllegalEscapeToUnicodeEscapeFix? {
            val illegalEscape = when (element) {
                is KtConstantExpression -> element.text.takeIf { it.length >= 2 }?.drop(1)?.dropLast(1)
                is KtEscapeStringTemplateEntry -> element.text
                else -> null
            } ?: return null
            val unicodeEscape = illegalEscapeToUnicodeEscape[illegalEscape] ?: return null

            return ConvertIllegalEscapeToUnicodeEscapeFix(element, unicodeEscape)
        }
    }
}

private val illegalEscapeToUnicodeEscape = mapOf("\\f" to "\\u000c")