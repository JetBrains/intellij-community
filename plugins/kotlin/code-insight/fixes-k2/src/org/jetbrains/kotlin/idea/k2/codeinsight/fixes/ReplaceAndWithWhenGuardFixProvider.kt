// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.analysis.ErrorQuickFixProvider
import com.intellij.psi.PsiErrorElement
import org.jetbrains.kotlin.idea.quickfix.ReplaceAndWithWhenGuardFix
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtWhenEntry
import org.jetbrains.kotlin.psi.KtWhenEntryGuard

class ReplaceAndWithWhenGuardFixProvider : ErrorQuickFixProvider {
    companion object {
        private const val ERROR_DESCRIPTION = "Unexpected '&&', use 'if' to introduce additional conditions; see https://kotl.in/guards-in-when"
    }

    private fun KtPsiFactory.createWhenEntryGuard(text: String): KtWhenEntryGuard? {
        return runCatching { createWhenEntry("is Int if $text -> {}").guard }.getOrNull()
    }

    override fun registerErrorQuickFix(errorElement: PsiErrorElement, builder: HighlightInfo.Builder) {
        val whenEntry = errorElement.parent as? KtWhenEntry ?: return
        if (whenEntry.isElse) return
        // Guards can only be used if there is a single condition
        if (whenEntry.conditions.size != 1) return
        if (errorElement.errorDescription != ERROR_DESCRIPTION) return

        if (whenEntry.isElse || whenEntry.guard != null) return
        val factory = KtPsiFactory(whenEntry.project)

        val conditionText = errorElement.text.trimStart().removePrefix("&&").trimStart()
        val newGuard = factory.createWhenEntryGuard(conditionText) ?: return
        val guardExpression = newGuard.getExpression()

        // If there is an '||' in the condition, we might change the order of operation and change semantics
        if (guardExpression is KtBinaryExpression && guardExpression.operationToken == KtTokens.OROR) return

        builder.registerFix(ReplaceAndWithWhenGuardFix(errorElement, newGuard), null, null, null, null)
    }
}