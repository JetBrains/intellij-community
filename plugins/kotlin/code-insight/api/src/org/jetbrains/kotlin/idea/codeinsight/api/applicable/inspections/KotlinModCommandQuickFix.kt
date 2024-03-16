// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections

import com.intellij.codeInspection.QuickFix
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.psi.KtElement

abstract class KotlinModCommandQuickFix<ELEMENT : KtElement> : PsiUpdateModCommandQuickFix() {

    /**
     * The action family name is an action name without any element-specific information.
     * For example, the family name for an action
     * "Replace 'get' call with indexing operator" would be "Replace 'get' or 'set' call with indexing operator".
     *
     * This is currently used as a fallback for when an element isn't available to build an action name, but may also be used in the future
     * as a group name for multiple quick fixes, as [QuickFix.getFamilyName] intends.
     * (Once the applicable inspections API supports multiple quick fixes.)
     */
    abstract override fun getFamilyName(): @IntentionFamilyName String

    /**
     * The text to be shown in the list of available fixes.
     */
    @Suppress("RedundantOverride")
    override fun getName(): String = super.getName()

    final override fun applyFix(
        project: Project,
        element: PsiElement,
        updater: ModPsiUpdater,
    ) {
        applyFix(
            project = project,
            element = @Suppress("UNCHECKED_CAST") (element as ELEMENT),
            updater = updater
        )
    }

    protected abstract fun applyFix(
        project: Project,
        element: ELEMENT,
        updater: ModPsiUpdater,
    )

    protected fun getName(
        smartPointer: SmartPsiElementPointer<ELEMENT>,
        name: (ELEMENT) -> @IntentionName String,
    ): @IntentionName String = runReadAction {
        smartPointer.element?.let(name)
    } ?: super.getName()
}
