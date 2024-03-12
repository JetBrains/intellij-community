// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes

import com.intellij.codeInsight.intention.CommonIntentionAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.modcommand.ModCommandAction
import com.intellij.openapi.diagnostic.ReportingClassSubstitutor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.diagnostics.KtDiagnosticWithPsi
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicator
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicatorInput
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.QuickFixActionBase
import kotlin.reflect.KClass

/*sealed*/ fun interface KotlinQuickFixFactory<DIAGNOSTIC : KtDiagnosticWithPsi<*>> {

    context(KtAnalysisSession)
    fun createQuickFixes(diagnostic: DIAGNOSTIC): List<CommonIntentionAction>

    /**
     * Creates [IntentionAction]s from a diagnostic.
     */
    fun interface IntentionBased<DIAGNOSTIC : KtDiagnosticWithPsi<*>> : KotlinQuickFixFactory<DIAGNOSTIC> {

        context(KtAnalysisSession)
        override fun createQuickFixes(diagnostic: DIAGNOSTIC): List<IntentionAction>
    }

    /**
     * Creates [ModCommandAction]s from a diagnostic.
     */
    fun interface ModCommandBased<DIAGNOSTIC : KtDiagnosticWithPsi<*>> : KotlinQuickFixFactory<DIAGNOSTIC> {

        context(KtAnalysisSession)
        override fun createQuickFixes(diagnostic: DIAGNOSTIC): List<ModCommandAction>
    }
}

@Deprecated("Use KotlinQuickFixFactory.IntentionBased")
sealed class KotlinDiagnosticFixFactory<DIAGNOSTIC : KtDiagnosticWithPsi<*>> : KotlinQuickFixFactory<DIAGNOSTIC> {

    context(KtAnalysisSession)
    abstract override fun createQuickFixes(diagnostic: DIAGNOSTIC): List<QuickFixActionBase<*>>

    abstract val diagnosticClass: KClass<DIAGNOSTIC>
}

private class KotlinDiagnosticFixFactoryWithFixedApplicator<DIAGNOSTIC : KtDiagnosticWithPsi<*>, TARGET_PSI : PsiElement, INPUT : KotlinApplicatorInput>(
    override val diagnosticClass: KClass<DIAGNOSTIC>,
    private val applicator: KotlinApplicator.PsiBased<TARGET_PSI, INPUT>,
    private val createTargets: context(KtAnalysisSession)(DIAGNOSTIC) -> List<KotlinApplicatorTargetWithInput<TARGET_PSI, INPUT>>,
) : KotlinDiagnosticFixFactory<DIAGNOSTIC>() {
    context(KtAnalysisSession)
    override fun createQuickFixes(diagnostic: DIAGNOSTIC): List<KotlinApplicatorBasedQuickFix<TARGET_PSI, INPUT>> =
        createTargets.invoke(this@KtAnalysisSession, diagnostic)
            .map { (target, input) -> KotlinApplicatorBasedQuickFix(target, input, applicator) }
}

private class KotlinDiagnosticFixFactoryUsingQuickFixActionBase<DIAGNOSTIC : KtDiagnosticWithPsi<*>>(
    override val diagnosticClass: KClass<DIAGNOSTIC>,
    private val createQuickFixes: context(KtAnalysisSession)(DIAGNOSTIC) -> List<QuickFixActionBase<*>>
) : KotlinDiagnosticFixFactory<DIAGNOSTIC>() {
    context(KtAnalysisSession)
    override fun createQuickFixes(diagnostic: DIAGNOSTIC): List<QuickFixActionBase<*>> {
        return createQuickFixes.invoke(this@KtAnalysisSession, diagnostic)
    }
}

/**
 * Returns a [KotlinDiagnosticFixFactory] that creates targets and inputs ([KotlinApplicatorTargetWithInput]) from a diagnostic.
 * The targets and inputs are consumed by the given applicator to apply fixes.
 */
fun <DIAGNOSTIC : KtDiagnosticWithPsi<*>, TARGET_PSI : PsiElement, INPUT : KotlinApplicatorInput> diagnosticFixFactory(
    diagnosticClass: KClass<DIAGNOSTIC>,
    applicator: KotlinApplicator.PsiBased<TARGET_PSI, INPUT>,
    createTargets: context(KtAnalysisSession)(DIAGNOSTIC) -> List<KotlinApplicatorTargetWithInput<TARGET_PSI, INPUT>>
): KotlinDiagnosticFixFactory<DIAGNOSTIC> =
    KotlinDiagnosticFixFactoryWithFixedApplicator(diagnosticClass, applicator, createTargets)

/**
 * Returns a [Collection] of [KotlinDiagnosticFixFactory] that creates [QuickFixActionBase]s from diagnostics that have the same type of
 * [PsiElement].
 */
fun <DIAGNOSTIC_PSI : PsiElement, DIAGNOSTIC : KtDiagnosticWithPsi<DIAGNOSTIC_PSI>> diagnosticFixFactories(
    vararg diagnosticClasses: KClass<out DIAGNOSTIC>,
    createQuickFixes: context(KtAnalysisSession)(DIAGNOSTIC) -> List<QuickFixActionBase<*>>
): Collection<KotlinDiagnosticFixFactory<out DIAGNOSTIC>> =
    diagnosticClasses.map { KotlinDiagnosticFixFactoryUsingQuickFixActionBase(it, createQuickFixes) }

/**
 * Returns a [KotlinDiagnosticFixFactory] that creates [IntentionAction]s from a diagnostic.
 */
fun <DIAGNOSTIC : KtDiagnosticWithPsi<*>> diagnosticFixFactoryFromIntentionActions(
    diagnosticClass: KClass<DIAGNOSTIC>,
    createIntentionActions: context(KtAnalysisSession)(DIAGNOSTIC) -> List<IntentionAction>
): KotlinDiagnosticFixFactory<DIAGNOSTIC> {
    // Wrap the IntentionActions as QuickFixActionBase. This ensures all fixes are of type QuickFixActionBase.
    val createQuickFixes: context(KtAnalysisSession) (DIAGNOSTIC) -> List<QuickFixActionBase<*>> = { diagnostic ->
        val intentionActions = createIntentionActions.invoke(analysisSession, diagnostic)
        intentionActions.map { IntentionActionAsQuickFixWrapper(it, diagnostic.psi) }
    }
    return KotlinDiagnosticFixFactoryUsingQuickFixActionBase(diagnosticClass, createQuickFixes)
}

/**
 * Returns a [Collection] of [KotlinDiagnosticFixFactory] that creates [IntentionAction]s from diagnostics that have the same type of
 * [PsiElement].
 */
fun <DIAGNOSTIC_PSI : PsiElement, DIAGNOSTIC : KtDiagnosticWithPsi<DIAGNOSTIC_PSI>> diagnosticFixFactoriesFromIntentionActions(
    vararg diagnosticClasses: KClass<out DIAGNOSTIC>,
    createIntentionActions: context(KtAnalysisSession)(DIAGNOSTIC) -> List<IntentionAction>
): Collection<KotlinDiagnosticFixFactory<out DIAGNOSTIC>> =
    diagnosticClasses.map { diagnosticFixFactoryFromIntentionActions(it, createIntentionActions) }

private class IntentionActionAsQuickFixWrapper<T : PsiElement>(val intentionAction: IntentionAction, element: T) :
    QuickFixActionBase<T>(element), ReportingClassSubstitutor {
    override fun getText(): String = intentionAction.text
    override fun getFamilyName(): String = intentionAction.familyName
    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) = intentionAction.invoke(project, editor, file)
    override fun startInWriteAction(): Boolean = intentionAction.startInWriteAction()
    override fun getSubstitutedClass(): Class<*> {
      return intentionAction.javaClass
    }
}
