// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.surroundWith.expression

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.idea.codeInsight.surroundWith.statement.KotlinIfSurrounderBase
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression

class KotlinIfElseExpressionSurrounder(private val withBraces: Boolean) : KotlinControlFlowExpressionSurrounderBase() {
    override fun getPattern() = if (withBraces) "if (a) { $0 } else {}" else "if (a) $0 else"

    @NlsSafe
    override fun getTemplateDescription() = if (withBraces) "if () { expr } else {}" else "if () expr else"

    override fun getRange(editor: Editor, replaced: KtExpression): TextRange? {
        val expression = when (replaced) {
            is KtParenthesizedExpression -> replaced.expression
            else -> replaced
        } as? KtIfExpression

        return expression?.let { KotlinIfSurrounderBase.getRange(editor, it) }
    }
}