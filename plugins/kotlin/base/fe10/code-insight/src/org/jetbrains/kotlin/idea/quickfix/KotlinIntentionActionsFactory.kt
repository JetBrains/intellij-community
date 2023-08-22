// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.QuickFixFactory
import org.jetbrains.kotlin.psi.KtCodeFragment

abstract class KotlinIntentionActionsFactory : QuickFixFactory {
    protected open fun isApplicableForCodeFragment(): Boolean = false

    protected abstract fun doCreateActions(diagnostic: Diagnostic): List<IntentionAction>

    open fun areActionsAvailable(diagnostic: Diagnostic): Boolean = createActions(diagnostic).isNotEmpty()

    protected open fun doCreateActionsForAllProblems(
        sameTypeDiagnostics: Collection<Diagnostic>
    ): List<IntentionAction> = emptyList()

    fun createActions(diagnostic: Diagnostic): List<IntentionAction> = createActions(listOf(diagnostic), false)

    fun createActionsForAllProblems(sameTypeDiagnostics: Collection<Diagnostic>): List<IntentionAction> =
        createActions(sameTypeDiagnostics, true)

    private fun createActions(sameTypeDiagnostics: Collection<Diagnostic>, createForAll: Boolean): List<IntentionAction> {
        if (sameTypeDiagnostics.isEmpty()) return emptyList()
        val first = sameTypeDiagnostics.first()

        if (first.psiElement.containingFile is KtCodeFragment && !isApplicableForCodeFragment()) {
            return emptyList()
        }

        if (sameTypeDiagnostics.size > 1 && createForAll) {
            assert(sameTypeDiagnostics.all { it.psiElement == first.psiElement && it.factory == first.factory }) {
                "It's expected to be the list of diagnostics of same type and for same element"
            }

            return doCreateActionsForAllProblems(sameTypeDiagnostics)
        }

        return sameTypeDiagnostics.flatMapTo(arrayListOf()) { doCreateActions(it) }
    }
}
