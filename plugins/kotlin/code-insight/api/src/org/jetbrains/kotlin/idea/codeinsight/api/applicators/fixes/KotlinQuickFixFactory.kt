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

/**
 * Returns a [KotlinQuickFixFactory.IntentionBased] that creates [IntentionAction]s from a diagnostic.
 */
fun <DIAGNOSTIC : KtDiagnosticWithPsi<*>> diagnosticFixFactoryFromIntentionActions(
    diagnosticClass: KClass<DIAGNOSTIC>,
    createIntentionActions: context(KtAnalysisSession)(DIAGNOSTIC) -> List<IntentionAction>,
) = KotlinQuickFixFactory.IntentionBased { diagnostic: DIAGNOSTIC ->
    // Wrap the IntentionActions as QuickFixActionBase. This ensures all fixes are of type QuickFixActionBase.
    createIntentionActions(analysisSession, diagnostic)
        .map { IntentionActionAsQuickFixWrapper(it, diagnostic.psi) }
}

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