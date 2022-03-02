// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.api.fixes

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.diagnostics.KtDiagnosticWithPsi
import org.jetbrains.kotlin.idea.quickfix.QuickFixesPsiBasedFactory
import org.jetbrains.kotlin.miniStdLib.annotations.PrivateForInline
import kotlin.reflect.KClass

class KtQuickFixesList @ForKtQuickFixesListBuilder @OptIn(PrivateForInline::class) constructor(
    private val quickFixes: Map<KClass<out KtDiagnosticWithPsi<*>>, List<HLQuickFixFactory>>
) {
    @OptIn(PrivateForInline::class)
    fun KtAnalysisSession.getQuickFixesFor(diagnostic: KtDiagnosticWithPsi<*>): List<IntentionAction> {
        val factories = quickFixes[diagnostic.diagnosticClass] ?: return emptyList()
        return factories.flatMap { createQuickFixes(it, diagnostic) }
    }

    @OptIn(PrivateForInline::class)
    private fun KtAnalysisSession.createQuickFixes(
        quickFixFactory: HLQuickFixFactory,
        diagnostic: KtDiagnosticWithPsi<*>
    ): List<IntentionAction> = when (quickFixFactory) {
        is HLQuickFixFactory.HLApplicatorBasedFactory -> {
            @Suppress("UNCHECKED_CAST")
            val factory = quickFixFactory.applicatorFactory
                    as HLDiagnosticFixFactory<KtDiagnosticWithPsi<PsiElement>>
            createPlatformQuickFixes(diagnostic, factory)
        }
        is HLQuickFixFactory.HLQuickFixesPsiBasedFactory -> quickFixFactory.psiFactory.createQuickFix(diagnostic.psi)
    }


    companion object {
        @OptIn(ForKtQuickFixesListBuilder::class, PrivateForInline::class)
        fun createCombined(registrars: List<KtQuickFixesList>): KtQuickFixesList {
            val allQuickFixes = registrars.map { it.quickFixes }.merge()
            return KtQuickFixesList(allQuickFixes)
        }

        fun createCombined(vararg registrars: KtQuickFixesList): KtQuickFixesList {
            return createCombined(registrars.toList())
        }
    }
}


class KtQuickFixesListBuilder private constructor() {
    @OptIn(PrivateForInline::class)
    private val quickFixes = mutableMapOf<KClass<out KtDiagnosticWithPsi<*>>, MutableList<HLQuickFixFactory>>()

    @OptIn(PrivateForInline::class)
    fun <DIAGNOSTIC_PSI : PsiElement, DIAGNOSTIC : KtDiagnosticWithPsi<DIAGNOSTIC_PSI>> registerPsiQuickFixes(
        diagnosticClass: KClass<DIAGNOSTIC>,
        vararg quickFixFactories: QuickFixesPsiBasedFactory<in DIAGNOSTIC_PSI>
    ) {
        for (quickFixFactory in quickFixFactories) {
            registerPsiQuickFix(diagnosticClass, quickFixFactory)
        }
    }

    @PrivateForInline
    fun <DIAGNOSTIC_PSI : PsiElement, DIAGNOSTIC : KtDiagnosticWithPsi<DIAGNOSTIC_PSI>> registerPsiQuickFix(
        diagnosticClass: KClass<DIAGNOSTIC>,
        quickFixFactory: QuickFixesPsiBasedFactory<in DIAGNOSTIC_PSI>
    ) {
        quickFixes.getOrPut(diagnosticClass) { mutableListOf() }.add(HLQuickFixFactory.HLQuickFixesPsiBasedFactory(quickFixFactory))
    }

    @OptIn(PrivateForInline::class)
    fun <DIAGNOSTIC : KtDiagnosticWithPsi<*>> registerApplicators(
        quickFixFactories: Collection<HLDiagnosticFixFactory<out DIAGNOSTIC>>
    ) {
        quickFixFactories.forEach(::registerApplicator)
    }

    @OptIn(PrivateForInline::class)
    fun <DIAGNOSTIC : KtDiagnosticWithPsi<*>> registerApplicator(
        quickFixFactory: HLDiagnosticFixFactory<out DIAGNOSTIC>
    ) {
        quickFixes.getOrPut(quickFixFactory.diagnosticClass) { mutableListOf() }
            .add(HLQuickFixFactory.HLApplicatorBasedFactory(quickFixFactory))
    }

    @OptIn(ForKtQuickFixesListBuilder::class, PrivateForInline::class)
    private fun build() = KtQuickFixesList(quickFixes)

    companion object {
        fun registerPsiQuickFix(init: KtQuickFixesListBuilder.() -> Unit) = KtQuickFixesListBuilder().apply(init).build()
    }
}

@PrivateForInline
sealed class HLQuickFixFactory {
    class HLQuickFixesPsiBasedFactory(
        val psiFactory: QuickFixesPsiBasedFactory<*>
    ) : HLQuickFixFactory()

    class HLApplicatorBasedFactory(
        val applicatorFactory: HLDiagnosticFixFactory<*>
    ) : HLQuickFixFactory()
}


private fun <K, V> List<Map<K, List<V>>>.merge(): Map<K, List<V>> {
    return flatMap { it.entries }
        .groupingBy { it.key }
        .aggregate<Map.Entry<K, List<V>>, K, MutableList<V>> { _, accumulator, element, _ ->
            val list = accumulator ?: mutableListOf()
            list.addAll(element.value)
            list
        }
}

@RequiresOptIn
annotation class ForKtQuickFixesListBuilder
