/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.PsiElementSuitabilityCheckers
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.QuickFixesPsiBasedFactory
import org.jetbrains.kotlin.psi.KtWhenEntry
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

class RemoveWhenBranchFix(
    element: KtWhenEntry,
    private val elseBranch: Boolean,
) : KotlinCrossLanguageQuickFixAction<KtWhenEntry>(element) {
    override fun getFamilyName(): String =
        if (elseBranch) KotlinBundle.message("remove.else.branch") else KotlinBundle.message("remove.branch")

    override fun getText(): String = familyName

    override fun invokeImpl(project: Project, editor: Editor?, file: PsiFile) {
        element?.delete()
    }

    companion object : QuickFixesPsiBasedFactory<PsiElement>(PsiElement::class, PsiElementSuitabilityCheckers.ALWAYS_SUITABLE) {
        override fun doCreateQuickFix(psiElement: PsiElement): List<IntentionAction> {
            val whenEntry = psiElement.getParentOfType<KtWhenEntry>(strict = false)
            return if (whenEntry != null && (whenEntry.isElse || whenEntry.conditions.size == 1)) {
                listOf(RemoveWhenBranchFix(whenEntry, whenEntry.isElse))
            } else {
                emptyList()
            }
        }
    }
}