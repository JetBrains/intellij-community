// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.psi.*

internal object ConvertIllegalEscapeToUnicodeEscapeFixFactory : KotlinSingleIntentionActionFactory() {
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
