// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.util.Ref
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.rendering.DefaultErrorMessages
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

abstract class KotlinSingleIntentionActionFactoryWithDelegate<E : KtElement, D : Any>(
    protected val actionPriority: IntentionActionPriority = IntentionActionPriority.NORMAL
) : KotlinIntentionActionFactoryWithDelegate<E, D>() {

    protected abstract fun createFix(originalElement: E, data: D): IntentionAction?

    override fun createFixes(
        originalElementPointer: SmartPsiElementPointer<E>,
        diagnostic: Diagnostic,
        quickFixDataFactory: (E) -> D?
    ): List<QuickFixWithDelegateFactory> = QuickFixWithDelegateFactory(actionPriority) factory@{
        val originalElement = originalElementPointer.element ?: return@factory null
        val data = quickFixDataFactory(originalElement) ?: return@factory null
        createFix(originalElement, data)
    }.let(::listOf)
}

abstract class KotlinIntentionActionFactoryWithDelegate<E : KtElement, D : Any> : KotlinIntentionActionsFactory() {
    abstract fun getElementOfInterest(diagnostic: Diagnostic): E?

    protected abstract fun createFixes(
        originalElementPointer: SmartPsiElementPointer<E>,
        diagnostic: Diagnostic,
        quickFixDataFactory: (E) -> D?
    ): List<QuickFixWithDelegateFactory>

    abstract fun extractFixData(element: E, diagnostic: Diagnostic): D?

    final override fun doCreateActions(diagnostic: Diagnostic): List<IntentionAction> {
        val diagnosticMessage = DefaultErrorMessages.render(diagnostic)
        val diagnosticElementPointer = diagnostic.psiElement.createSmartPointer()
        val originalElement = getElementOfInterest(diagnostic) ?: return emptyList()
        val originalElementPointer = originalElement.createSmartPointer()

        val file = originalElement.containingFile
        val project = file.project

        // Cache data so that it can be shared between quick fixes bound to the same element & diagnostic
        // Cache null values
        val cachedData: Ref<D> = Ref.create(extractFixData(originalElement, diagnostic))

        return try {
            createFixes(originalElementPointer, diagnostic) factory@{
                val element = originalElementPointer.element ?: return@factory null
                val diagnosticElement = diagnosticElementPointer.element ?: return@factory null
                if (!diagnosticElement.isValid || !element.isValid) return@factory null

                val currentDiagnostic = element.analyze(BodyResolveMode.PARTIAL_WITH_DIAGNOSTICS)
                    .diagnostics
                    .forElement(diagnosticElement)
                    .firstOrNull { DefaultErrorMessages.render(it) == diagnosticMessage } ?: return@factory null

                cachedData.get() ?: extractFixData(element, currentDiagnostic)
            }.filter { it.isAvailable(project, null, file) }
        } finally {
            cachedData.set(null) // Do not keep cache after all actions are initialized
        }
    }
}
