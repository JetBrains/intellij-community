// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes

import com.intellij.codeInsight.intention.CommonIntentionAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.diagnostics.KtDiagnosticWithPsi
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.QuickFixesPsiBasedFactory
import org.jetbrains.kotlin.miniStdLib.annotations.PrivateForInline
import kotlin.reflect.KClass

class KotlinQuickFixesList @ForKtQuickFixesListBuilder constructor(
    private val quickFixes: Map<KClass<out KtDiagnosticWithPsi<*>>, List<KotlinQuickFixFactory<*>>>
) {
    context(KtAnalysisSession)
    fun getQuickFixesFor(diagnostic: KtDiagnosticWithPsi<*>): List<IntentionAction> {
        val factories = quickFixes[diagnostic.diagnosticClass]
            ?: return emptyList()

        return factories.asSequence()
            .map { @Suppress("UNCHECKED_CAST") (it as KotlinQuickFixFactory<KtDiagnosticWithPsi<*>>) }
            .flatMap { it.createQuickFixes(diagnostic) }
            .map { it.asIntention() }
            .toList()
    }

    companion object {
        @OptIn(ForKtQuickFixesListBuilder::class)
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

    private val quickFixes = LinkedHashMap<
            KClass<out KtDiagnosticWithPsi<*>>,
            MutableList<KotlinQuickFixFactory<out KtDiagnosticWithPsi<*>>>,
            >()

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
        quickFixes.getOrPut(diagnosticClass) { mutableListOf() }
            .add(KotlinQuickFixesPsiBasedFactory(quickFixFactory))
    }

    fun <DIAGNOSTIC : KtDiagnosticWithPsi<*>> registerApplicators(
        quickFixFactories: Collection<KotlinDiagnosticFixFactory<out DIAGNOSTIC>>
    ) {
        quickFixFactories.forEach(::registerApplicator)
    }

    fun <DIAGNOSTIC : KtDiagnosticWithPsi<*>> registerApplicator(
        quickFixFactory: KotlinDiagnosticFixFactory<out DIAGNOSTIC>
    ) {
        quickFixes.getOrPut(quickFixFactory.diagnosticClass) { mutableListOf() }
            .add(KotlinApplicatorBasedFactory(quickFixFactory))
    }

    inline fun <reified DIAGNOSTIC : KtDiagnosticWithPsi<*>> registerFactory(
        factory: KotlinQuickFixFactory.ModCommandBased<DIAGNOSTIC>,
    ) {
        registerFactory(DIAGNOSTIC::class, factory)
    }

    fun <DIAGNOSTIC : KtDiagnosticWithPsi<*>> registerFactory(
        diagnosticClass: KClass<DIAGNOSTIC>,
        factory: KotlinQuickFixFactory<DIAGNOSTIC>,
    ) {
        quickFixes.getOrPut(diagnosticClass) { mutableListOf() } += factory
    }

    @OptIn(ForKtQuickFixesListBuilder::class)
    private fun build() = KotlinQuickFixesList(quickFixes)

    companion object {
        fun registerPsiQuickFix(init: KtQuickFixesListBuilder.() -> Unit) = KtQuickFixesListBuilder().apply(init).build()
    }
}

private class KotlinQuickFixesPsiBasedFactory(
    private val delegate: QuickFixesPsiBasedFactory<*>,
) : KotlinQuickFixFactory<KtDiagnosticWithPsi<*>> {

    context(KtAnalysisSession)
    override fun createQuickFixes(diagnostic: KtDiagnosticWithPsi<*>): List<CommonIntentionAction> =
        delegate.createQuickFix(diagnostic.psi)
}

private class KotlinApplicatorBasedFactory<DIAGNOSTIC : KtDiagnosticWithPsi<*>>(
    private val delegate: KotlinDiagnosticFixFactory<DIAGNOSTIC>,
) : KotlinQuickFixFactory<DIAGNOSTIC> {

    context(KtAnalysisSession)
    override fun createQuickFixes(diagnostic: DIAGNOSTIC): List<CommonIntentionAction> =
        delegate.createQuickFixes(diagnostic)
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
