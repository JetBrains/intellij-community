// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.diagnostics.KaDiagnosticWithPsi
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.QuickFixesPsiBasedFactory
import kotlin.reflect.KClass

class KotlinQuickFixesList @ForKtQuickFixesListBuilder constructor(
    private val quickFixes: Map<KClass<out KaDiagnosticWithPsi<*>>, List<KotlinQuickFixFactory<*>>>
) {
    fun KaSession.canProduceQuickFixesFor(diagnostic: KaDiagnosticWithPsi<*>): Boolean =
        quickFixes[diagnostic.diagnosticClass]?.isNotEmpty() == true

    fun KaSession.getQuickFixesFor(diagnostic: KaDiagnosticWithPsi<*>): List<IntentionAction> {
        val fixes = getQuickFixesWithCatchingFor(diagnostic)
        return fixes
            .mapTo(mutableListOf()) { it.getOrThrow() }
    }

    fun KaSession.getQuickFixesWithCatchingFor(diagnostic: KaDiagnosticWithPsi<*>): Sequence<Result<IntentionAction>> {
        val factories = quickFixes[diagnostic.diagnosticClass]
            ?: return emptySequence()

        return factories.asSequence()
            .map { @Suppress("UNCHECKED_CAST") (it as KotlinQuickFixFactory<KaDiagnosticWithPsi<*>>) }
            .map { factory ->
                with(factory) {
                    runCatching { createQuickFixes(diagnostic).map { it.asIntention() } }
                        .recoverCatching { throwable ->
                            when (throwable) {
                                is ProcessCanceledException -> throw throwable
                                else -> throw ComputingQuickfixesError("Error while creating quickfixes by ${factory}", throwable)
                            }
                        }
                }
            }.flatMap { r ->
                r.fold(
                    onSuccess = { actions -> actions.map { Result.success(it) }.asSequence() },
                    onFailure = { sequenceOf(Result.failure(it)) },
                )
            }

    }

    class ComputingQuickfixesError(message: String, cause: Throwable) : IllegalStateException(message, cause)

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
            KClass<out KaDiagnosticWithPsi<*>>,
            MutableList<KotlinQuickFixFactory<out KaDiagnosticWithPsi<*>>>,
            >()

    fun <DIAGNOSTIC_PSI : PsiElement, DIAGNOSTIC : KaDiagnosticWithPsi<DIAGNOSTIC_PSI>> registerPsiQuickFixes(
        diagnosticClass: KClass<DIAGNOSTIC>,
        vararg factories: QuickFixesPsiBasedFactory<in DIAGNOSTIC_PSI>,
    ) {
        for (factory in factories) {
            registerFactory(diagnosticClass) { diagnostic: DIAGNOSTIC ->
                diagnostic.psi.let(factory::createQuickFix) ?: emptyList()
            }
        }
    }

    inline fun <reified DIAGNOSTIC : KaDiagnosticWithPsi<*>> registerFactory(
        factory: KotlinQuickFixFactory<DIAGNOSTIC>,
    ) {
        registerFactory(DIAGNOSTIC::class, factory)
    }

    fun <DIAGNOSTIC : KaDiagnosticWithPsi<*>> registerFactory(
        diagnosticClass: KClass<DIAGNOSTIC>,
        factory: KotlinQuickFixFactory<DIAGNOSTIC>,
    ) {
        if (diagnosticClass == KaDiagnosticWithPsi::class) {
            logger<KtQuickFixesListBuilder>().error(
                """
                Specific diagnostic class expected instead of generic ${KaDiagnosticWithPsi::class}.
                Factory registered this way would never be used.
                The registered factory class was: ${factory::class}.
                """.trimIndent()
            )
        }

        quickFixes.getOrPut(diagnosticClass) { mutableListOf() } += factory
    }

    @OptIn(ForKtQuickFixesListBuilder::class)
    private fun build() = KotlinQuickFixesList(quickFixes)

    companion object {
        fun registerPsiQuickFix(init: KtQuickFixesListBuilder.() -> Unit): KotlinQuickFixesList = KtQuickFixesListBuilder().apply(init).build()
    }
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
