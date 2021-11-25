// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtPsiFactory

class ChangeToFunctionInvocationFix(element: KtExpression) : KotlinQuickFixAction<KtExpression>(element) {
    override fun getFamilyName() = KotlinBundle.message("fix.change.to.function.invocation")

    override fun getText() = familyName

    public override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element ?: return
        val psiFactory = KtPsiFactory(element)
        val nextLiteralStringEntry = element.parent.nextSibling as? KtLiteralStringTemplateEntry
        val nextText = nextLiteralStringEntry?.text
        if (nextText != null && nextText.startsWith("(") && nextText.contains(")")) {
            val parentheses = nextText.takeWhile { it != ')' } + ")"
            val newNextText = nextText.removePrefix(parentheses)
            if (newNextText.isNotEmpty()) {
                nextLiteralStringEntry.replace(psiFactory.createLiteralStringTemplateEntry(newNextText))
            } else {
                nextLiteralStringEntry.delete()
            }
            element.replace(KtPsiFactory(file).createExpression("${element.text}$parentheses"))
        } else {
            element.replace(KtPsiFactory(file).createExpression("${element.text}()"))
        }
    }

    companion object : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): KotlinQuickFixAction<KtExpression>? {
            val expression = diagnostic.psiElement as? KtExpression ?: return null
            return ChangeToFunctionInvocationFix(expression)
        }
    }
}
