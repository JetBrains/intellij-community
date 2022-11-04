// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix.replaceWith

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeInliner.UsageReplacementStrategy
import org.jetbrains.kotlin.idea.core.moveCaret
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.CleanupFix
import org.jetbrains.kotlin.idea.quickfix.KotlinSingleIntentionActionFactory
import com.intellij.openapi.application.runWriteAction
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.resolve.calls.util.getCalleeExpressionIfAny

class DeprecatedSymbolUsageFix(
    element: KtReferenceExpression,
    replaceWith: ReplaceWithData
) : DeprecatedSymbolUsageFixBase(element, replaceWith), CleanupFix, HighPriorityAction {
    override fun getFamilyName() = KotlinBundle.message("replace.deprecated.symbol.usage")
    override fun getText() = KotlinBundle.message("replace.with.0", replaceWith.pattern)

    override fun invoke(replacementStrategy: UsageReplacementStrategy, project: Project, editor: Editor?) {
        val element = element ?: return
        val replacer = replacementStrategy.createReplacer(element) ?: return
        val result = replacer() ?: return

        if (editor != null) {
            val offset = (result.getCalleeExpressionIfAny() ?: result).textOffset
            editor.moveCaret(offset)
        }
    }

    companion object : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val (referenceExpression, replacement) = extractDataFromDiagnostic(diagnostic, false) ?: return null
            return DeprecatedSymbolUsageFix(referenceExpression, replacement).takeIf { it.isAvailable }
        }
    }
}
