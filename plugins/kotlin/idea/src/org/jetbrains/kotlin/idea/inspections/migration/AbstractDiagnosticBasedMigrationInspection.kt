// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections.migration

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.DiagnosticFactoryWithPsiElement
import org.jetbrains.kotlin.diagnostics.rendering.DefaultErrorMessages
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithAllCompilerChecks
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.quickfix.QuickFixes
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid


abstract class AbstractDiagnosticBasedMigrationInspection<T : PsiElement>(val elementType: Class<T>) : AbstractKotlinInspection() {
    abstract fun getDiagnosticFactory(languageVersionSettings: LanguageVersionSettings): DiagnosticFactoryWithPsiElement<T, *>
    open fun customIntentionFactory(): ((Diagnostic) -> IntentionAction?)? = null
    open fun customHighlightRangeIn(element: T): TextRange? = null

    private fun getActionFactory(languageVersionSettings: LanguageVersionSettings): (Diagnostic) -> List<IntentionAction> =
        customIntentionFactory()?.let { factory -> { diagnostic -> listOfNotNull(factory(diagnostic)) } }
            ?: QuickFixes.getInstance()
                .getActionFactories(getDiagnosticFactory(languageVersionSettings))
                .singleOrNull()
                ?.let { factory -> { diagnostic -> factory.createActions(diagnostic) } }
            ?: error("Must have one factory")

    override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
        if (file !is KtFile) return null
        val diagnostics by lazy {
            file.analyzeWithAllCompilerChecks().bindingContext.diagnostics
        }

        val problemDescriptors = mutableListOf<ProblemDescriptor>()
        val languageVersionSettings = file.languageVersionSettings
        val actionFactory = getActionFactory(languageVersionSettings)
        val diagnosticFactory = getDiagnosticFactory(languageVersionSettings)

        file.accept(
            object : KtTreeVisitorVoid() {
                override fun visitElement(element: PsiElement) {
                    super.visitElement(element)

                    if (!elementType.isInstance(element) || element.textLength == 0) return
                    val diagnostic = diagnostics.forElement(element)
                        .filter { it.factory == diagnosticFactory }
                        .ifEmpty { return }
                        .singleOrNull()
                        ?: error("Must have one diagnostic")

                    val intentionAction = actionFactory(diagnostic).ifEmpty { return }.singleOrNull() ?: error("Must have one fix")
                    val text = descriptionMessage() ?: DefaultErrorMessages.render(diagnostic)

                    problemDescriptors.add(
                        manager.createProblemDescriptor(
                            element,
                            @Suppress("UNCHECKED_CAST") customHighlightRangeIn(element as T),
                            text,
                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                            false,
                            IntentionWrapper(intentionAction),
                        )
                    )
                }
            },
        )

        return problemDescriptors.toTypedArray()
    }

    @Nls
    protected open fun descriptionMessage(): String? = null
}