// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.google.common.collect.HashMultimap
import com.google.common.collect.Multimap
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.Extensions
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.QuickFixFactory
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.UnresolvedReferenceQuickFixFactory
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty

class QuickFixes {
    private val factories: Multimap<DiagnosticFactory<*>, KotlinIntentionActionsFactory> =
        HashMultimap.create<DiagnosticFactory<*>, KotlinIntentionActionsFactory>()
    private val unresolvedReferenceActionFactories: Multimap<DiagnosticFactory<*>, KotlinIntentionActionsFactory> =
        HashMultimap.create<DiagnosticFactory<*>, KotlinIntentionActionsFactory>()
    private val actions: Multimap<DiagnosticFactory<*>, IntentionAction> = HashMultimap.create<DiagnosticFactory<*>, IntentionAction>()

    init {
        @Suppress("DEPRECATION")
        Extensions.getExtensions(QuickFixContributor.EP_NAME).forEach { it.registerQuickFixes(this) }
    }

    fun register(diagnosticFactory: DiagnosticFactory<*>, vararg factory: QuickFixFactory) {
        factories.putAll(diagnosticFactory, factory.map { it.asKotlinIntentionActionsFactory() })
        factory.filterIsInstance<UnresolvedReferenceQuickFixFactory>().ifNotEmpty {
            unresolvedReferenceActionFactories.putAll(diagnosticFactory, this.map { it.asKotlinIntentionActionsFactory() })
        }
    }

    fun register(diagnosticFactory: DiagnosticFactory<*>, vararg factory: KotlinIntentionActionsFactory) {
        factories.putAll(diagnosticFactory, factory.toList())
        factory.filterIsInstance<UnresolvedReferenceQuickFixFactory>().ifNotEmpty {
            unresolvedReferenceActionFactories.putAll(diagnosticFactory, this.map { it.asKotlinIntentionActionsFactory() })
        }
    }

    fun register(diagnosticFactory: DiagnosticFactory<*>, vararg action: IntentionAction) {
        actions.putAll(diagnosticFactory, action.toList())
    }

    fun getActionFactories(diagnosticFactory: DiagnosticFactory<*>): Collection<KotlinIntentionActionsFactory> {
        return factories.get(diagnosticFactory)
    }

    fun getUnresolvedReferenceActionFactories(diagnosticFactory: DiagnosticFactory<*>): Collection<KotlinIntentionActionsFactory> {
        return unresolvedReferenceActionFactories.get(diagnosticFactory)
    }

    fun getActions(diagnosticFactory: DiagnosticFactory<*>): Collection<IntentionAction> {
        return actions.get(diagnosticFactory)
    }

    fun getDiagnostics(factory: KotlinIntentionActionsFactory): Collection<DiagnosticFactory<*>> {
        return factories.keySet().filter { factory in factories.get(it) }
    }

    companion object {
        fun getInstance(): QuickFixes = service()
    }
}