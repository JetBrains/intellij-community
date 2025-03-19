// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes

import com.intellij.codeInsight.intention.FileModifier
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.permissions.forbidAnalysis
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.psi.KtFile

@Deprecated("Prefer using KotlinApplicableModCommandAction")
abstract class KotlinApplicatorBasedQuickFix<T : PsiElement, in I : KotlinApplicatorBasedQuickFix.Input>(
    element: T,
    @FileModifier.SafeFieldForPreview
    private val input: I,
) : KotlinQuickFixAction<T>(element) {

    /**
     * Data needed to perform the fix
     *
     * Should not store inside
     * - Everything that came from [org.jetbrains.kotlin.analysis.api.KaSession] like :
     *      - [org.jetbrains.kotlin.analysis.api.symbols.KtSymbol] consider using [org.jetbrains.kotlin.analysis.api.symbols.pointers.KaSymbolPointer] instead
     *      - [org.jetbrains.kotlin.analysis.api.types.KaType]
     *      - [org.jetbrains.kotlin.analysis.api.resolution.KaCall]
     * - [org.jetbrains.kotlin.analysis.api.KaSession] instance itself
     * - [PsiElement] consider using [com.intellij.psi.SmartPsiElementPointer] instead
     *
     */
    interface Input {

        fun isValidFor(psi: PsiElement): Boolean = true

        companion object Empty : Input
    }

    override fun invoke(
        project: Project,
        editor: Editor?,
        file: KtFile,
    ) {
        val element = element ?: return

        val isApplicableByPsi = forbidAnalysis("KotlinApplicator.isApplicableByPsi") {
            isApplicableByPsi(element, project)
        }

        if (!isApplicableByPsi
            || !input.isValidFor(element)
        ) return

        invoke(element, input, project, editor)
    }

    override fun getText(): String =
        element?.takeIf { input.isValidFor(it) }
            ?.let { getActionName(it, input) }
            ?: familyName

    /**
     * Action name which will be as text in inspections/intentions
     *
     * @see com.intellij.codeInsight.intention.IntentionAction.getText
     */
    protected open fun getActionName(
        element: T,
        input: I,
    ): @IntentionName String? = null

    /**
     * Checks if applicator is applicable to a specific element, can not use resolve inside
     */
    protected open fun isApplicableByPsi(
        element: T,
        project: Project,
    ): Boolean = true

    /**
     * Applies some fix to given [element], cannot use resolve, so all needed data should be precalculated and stored in [input]
     *
     * @param element a [PsiElement] to apply fix to
     * @param input additional data needed to apply the fix
     */
    protected abstract fun invoke(
        element: T,
        input: I,
        project: Project,
        editor: Editor?,
    )
}