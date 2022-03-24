// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.diagnostics.Diagnostic

abstract class KotlinSingleIntentionActionFactory : KotlinIntentionActionsFactory() {
    protected abstract fun createAction(diagnostic: Diagnostic): IntentionAction?

    final override fun doCreateActions(diagnostic: Diagnostic): List<IntentionAction> = listOfNotNull(createAction(diagnostic))

    companion object {
        inline fun <reified PSI : PsiElement> createFromQuickFixesPsiBasedFactory(
            psiBasedFactory: QuickFixesPsiBasedFactory<PSI>
        ): KotlinSingleIntentionActionFactory = object : KotlinSingleIntentionActionFactory() {
            override fun createAction(diagnostic: Diagnostic): IntentionAction? {
                val factories = psiBasedFactory.createQuickFix(diagnostic.psiElement as PSI)
                return when (factories.size) {
                    0 -> null
                    1 -> factories.single()
                    else -> error("To convert QuickFixesPsiBasedFactory to KotlinSingleIntentionActionFactory, it should always return one or zero quickfixes")
                }
            }
        }
    }
}

fun QuickFixFactory.asKotlinIntentionActionsFactory(): KotlinIntentionActionsFactory = when (this) {
    is KotlinIntentionActionsFactory -> this
    is QuickFixesPsiBasedFactory<*> -> object : KotlinIntentionActionsFactory() {
        @Suppress("UNCHECKED_CAST")
        override fun doCreateActions(diagnostic: Diagnostic): List<IntentionAction> {
            val psiElement = diagnostic.psiElement
            return createQuickFix(psiElement)
        }
    }
    else -> error("Unexpected QuickFixFactory ${this::class}")
}