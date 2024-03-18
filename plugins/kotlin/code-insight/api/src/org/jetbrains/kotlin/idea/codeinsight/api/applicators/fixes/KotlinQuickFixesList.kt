// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.diagnostics.KtDiagnosticWithPsi
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.QuickFixesPsiBasedFactory
import org.jetbrains.kotlin.miniStdLib.annotations.PrivateForInline
import kotlin.reflect.KClass

class KotlinQuickFixesList @ForKtQuickFixesListBuilder @OptIn(PrivateForInline::class) constructor(
    private val quickFixes: Map<KClass<out KtDiagnosticWithPsi<*>>, List<KotlinQuickFixFactory>>
) {
    context(KtAnalysisSession)
    @OptIn(PrivateForInline::class)
    fun getQuickFixesFor(diagnostic: KtDiagnosticWithPsi<*>): List<IntentionAction> {
        val factories = quickFixes[diagnostic.diagnosticClass] ?: return emptyList()
        return factories.flatMap { createQuickFixes(it, diagnostic) }
    }

    context(KtAnalysisSession)
    @OptIn(PrivateForInline::class)
    private fun createQuickFixes(
        quickFixFactory: KotlinQuickFixFactory,
        diagnostic: KtDiagnosticWithPsi<*>
    ): List<IntentionAction> = when (quickFixFactory) {
        is KotlinQuickFixFactory.KotlinApplicatorBasedFactory -> {
            @Suppress("UNCHECKED_CAST")
            val factory = quickFixFactory.applicatorFactory
                    as KotlinDiagnosticFixFactory<KtDiagnosticWithPsi<PsiElement>>
            createPlatformQuickFixes(diagnostic, factory)
        }
        is KotlinQuickFixFactory.KotlinApplicatorModCommandBasedFactory -> {
            @Suppress("UNCHECKED_CAST")
            val factory = quickFixFactory.applicatorFactory
                    as KotlinDiagnosticModCommandFixFactory<KtDiagnosticWithPsi<PsiElement>>
            createPlatformQuickFixes(diagnostic, factory)
        }

        is KotlinQuickFixFactory.KotlinQuickFixesPsiBasedFactory -> quickFixFactory.psiFactory.createQuickFix(diagnostic.psi)
    }

    /**
     * Returns new `KotlinQuickFixesList`, which creates only quick fixes produced by factories from original list that are [T].
     */
    @OptIn(ForKtQuickFixesListBuilder::class, PrivateForInline::class)
    internal inline fun <reified T> filterByFactoryType(): KotlinQuickFixesList  {
        val fixes = quickFixes.mapValues { (_, factories) ->
            factories.filter { factory ->
                when (factory) {
                    is KotlinQuickFixFactory.KotlinApplicatorBasedFactory -> factory.applicatorFactory is T
                    is KotlinQuickFixFactory.KotlinQuickFixesPsiBasedFactory -> factory.psiFactory is T
                    is KotlinQuickFixFactory.KotlinApplicatorModCommandBasedFactory -> factory.applicatorFactory is T
                }
            }
        }
        return KotlinQuickFixesList(fixes)
    }

    companion object {
        @OptIn(ForKtQuickFixesListBuilder::class, PrivateForInline::class)
        fun createCombined(registrars: List<KotlinQuickFixesList>): KotlinQuickFixesList {
            val allQuickFixes = registrars.map { it.quickFixes }.merge()
            return KotlinQuickFixesList(allQuickFixes)
        }

        fun createCombined(vararg registrars: KotlinQuickFixesList): KotlinQuickFixesList {
            return createCombined(registrars.toList())
        }
    }
}


class KtQuickFixesListBuilder private constructor() {
    @OptIn(PrivateForInline::class)
    private val quickFixes = mutableMapOf<KClass<out KtDiagnosticWithPsi<*>>, MutableList<KotlinQuickFixFactory>>()

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
        quickFixes.getOrPut(diagnosticClass) { mutableListOf() }.add(KotlinQuickFixFactory.KotlinQuickFixesPsiBasedFactory(quickFixFactory))
    }

    fun <DIAGNOSTIC : KtDiagnosticWithPsi<*>> registerApplicators(
        quickFixFactories: Collection<KotlinDiagnosticFixFactory<out DIAGNOSTIC>>
    ) {
        quickFixFactories.forEach(::registerApplicator)
    }

    fun <DIAGNOSTIC : KtDiagnosticWithPsi<*>> registerModCommandApplicators(
        quickFixFactories: Collection<KotlinDiagnosticModCommandFixFactory<out DIAGNOSTIC>>
    ) {
        quickFixFactories.forEach(::registerApplicator)
    }

    @OptIn(PrivateForInline::class)
    fun <DIAGNOSTIC : KtDiagnosticWithPsi<*>> registerApplicator(
        quickFixFactory: KotlinDiagnosticFixFactory<out DIAGNOSTIC>
    ) {
        quickFixes.getOrPut(quickFixFactory.diagnosticClass) { mutableListOf() }
            .add(KotlinQuickFixFactory.KotlinApplicatorBasedFactory(quickFixFactory))
    }

    @OptIn(PrivateForInline::class)
    fun <DIAGNOSTIC : KtDiagnosticWithPsi<*>> registerApplicator(
        quickFixFactory: KotlinDiagnosticModCommandFixFactory<out DIAGNOSTIC>
    ) {
        quickFixes.getOrPut(quickFixFactory.diagnosticClass) { mutableListOf() }
            .add(KotlinQuickFixFactory.KotlinApplicatorModCommandBasedFactory(quickFixFactory))
    }

    @OptIn(ForKtQuickFixesListBuilder::class, PrivateForInline::class)
    private fun build() = KotlinQuickFixesList(quickFixes)

    companion object {
        fun registerPsiQuickFix(init: KtQuickFixesListBuilder.() -> Unit) = KtQuickFixesListBuilder().apply(init).build()
    }
}

@PrivateForInline
sealed class KotlinQuickFixFactory {
    class KotlinQuickFixesPsiBasedFactory(
        val psiFactory: QuickFixesPsiBasedFactory<*>
    ) : KotlinQuickFixFactory()

    class KotlinApplicatorBasedFactory(
        val applicatorFactory: KotlinDiagnosticFixFactory<*>
    ) : KotlinQuickFixFactory()

    class KotlinApplicatorModCommandBasedFactory(
        val applicatorFactory: KotlinDiagnosticModCommandFixFactory<*>
    ) : KotlinQuickFixFactory()
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
