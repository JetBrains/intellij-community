// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeinsight.api.applicators

import com.intellij.codeInsight.intention.FileModifier
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

/**
 * Applies a fix to the PSI, used as intention/inspection/quickfix action
 *
 * Uses some additional information from [INPUT] to apply the element
 */
@FileModifier.SafeTypeForPreview
sealed interface KotlinApplicator<in PSI : PsiElement, in INPUT : KotlinApplicatorInput> {

    /**
     * Action name which will be as text in inspections/intentions
     *
     * @see com.intellij.codeInsight.intention.IntentionAction.getText
     */
    fun getActionName(
        psi: PSI,
        input: INPUT,
    ): @IntentionName String = getFamilyName()

    /**
     * Family name which will be used in inspections/intentions
     *
     * @see com.intellij.codeInsight.intention.IntentionAction.getFamilyName
     */
    fun getFamilyName(): @IntentionFamilyName String

    @Deprecated("prefer ModCommandBased", replaceWith = ReplaceWith("ModCommandBased<PSI, INPUT>"))
    interface PsiBased<in PSI : PsiElement, in INPUT : KotlinApplicatorInput> : KotlinApplicator<PSI, INPUT> {

        /**
         * Checks if applicator is applicable to a specific element, can not use resolve inside
         */
        fun isApplicableByPsi(psi: PSI, project: Project): Boolean = true

        /**
         * Applies some fix to given [psi], cannot use resolve, so all needed data should be precalculated and stored in [input]
         *
         * @param psi a [PsiElement] to apply fix to
         * @param input additional data needed to apply the fix
         */
        fun applyTo(
            psi: PSI,
            input: INPUT,
            project: Project,
            editor: Editor?,
        )

        fun startInWriteAction(): Boolean = true
    }
}
