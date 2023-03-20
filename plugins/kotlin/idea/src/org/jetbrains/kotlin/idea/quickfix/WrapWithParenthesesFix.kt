// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.psi.*

/**
 * Tests:
 * [org.jetbrains.kotlin.idea.quickfix.QuickFixTestGenerated.WrapArgumentWithParentheses]
 */
class WrapWithParenthesesFix(element: KtExpression, @IntentionName private val _text: String) :
    KotlinQuickFixAction<KtExpression>(element) {
    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        element?.let { it.replace(KtPsiFactory(project).createExpressionByPattern("($0)", it)) }
    }

    override fun getText(): String = _text
    override fun getFamilyName(): String = _text

    object ModifierFormForNonBuiltInSuspend : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? =
            diagnostic.psiElement.parentOfType<KtBinaryExpression>()
                ?.takeIf { it.operationReference.text == "suspend" }
                ?.right
                ?.let { WrapWithParenthesesFix(it, KotlinBundle.message("wrap.argument.with.parentheses")) }
    }
}
