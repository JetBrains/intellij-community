// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("QuickFixFactoryUtils")
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.QuickFixFactory
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.QuickFixesPsiBasedFactory

fun QuickFixFactory.asKotlinIntentionActionsFactory(): KotlinIntentionActionsFactory = when (this) {
    is KotlinIntentionActionsFactory -> this
    is QuickFixesPsiBasedFactory<*> -> object : KotlinIntentionActionsFactory() {
        override fun doCreateActions(diagnostic: Diagnostic): List<IntentionAction> {
            val psiElement = diagnostic.psiElement
            return createQuickFix(psiElement)
        }
    }
    else -> error("Unexpected QuickFixFactory ${this::class}")
}