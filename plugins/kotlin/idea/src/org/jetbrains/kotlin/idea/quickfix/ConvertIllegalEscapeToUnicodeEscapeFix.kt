// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.psi.*

class ConvertIllegalEscapeToUnicodeEscapeFix(
    element: KtElement,
    private val unicodeEscape: String
) : KotlinQuickFixAction<KtElement>(element) {
    override fun getText(): String = KotlinBundle.message("convert.to.unicode.escape")

    override fun getFamilyName(): String = text

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = this.element ?: return
        val psiFactory = KtPsiFactory(project)
        when (element) {
            is KtConstantExpression -> element.replace(psiFactory.createExpression("'$unicodeEscape'"))
            is KtEscapeStringTemplateEntry -> element.replace(psiFactory.createStringTemplate(unicodeEscape).entries.first())
        }
    }

    companion object : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val element = diagnostic.psiElement as? KtElement ?: return null
            val illegalEscape = when (element) {
                is KtConstantExpression -> element.text.takeIf { it.length >= 2 }?.drop(1)?.dropLast(1)
                is KtEscapeStringTemplateEntry -> element.text
                else -> null
            } ?: return null
            val unicodeEscape = illegalEscapeToUnicodeEscape[illegalEscape] ?: return null
            return ConvertIllegalEscapeToUnicodeEscapeFix(element, unicodeEscape)
        }

        private val illegalEscapeToUnicodeEscape = mapOf("\\f" to "\\u000c")
    }
}
