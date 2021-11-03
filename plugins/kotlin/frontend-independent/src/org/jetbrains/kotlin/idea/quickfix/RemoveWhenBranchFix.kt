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
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtWhenEntry
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

class RemoveWhenBranchFix(
    element: KtWhenEntry,
) : KotlinCrossLanguageQuickFixAction<KtWhenEntry>(element) {
    override fun getFamilyName() = if (element?.isElse == true) {
        KotlinBundle.message("remove.else.branch")
    } else {
        KotlinBundle.message("remove.branch")
    }

    override fun getText() = familyName

    override fun invokeImpl(project: Project, editor: Editor?, file: PsiFile) {
        element?.delete()
    }

    companion object : QuickFixesPsiBasedFactory<PsiElement>(PsiElement::class, PsiElementSuitabilityCheckers.ALWAYS_SUITABLE) {
        override fun doCreateQuickFix(psiElement: PsiElement): List<IntentionAction> {
            val whenEntry = psiElement.getParentOfType<KtWhenEntry>(strict = false)
                ?.takeIf { it.conditions.size == 1 }
                ?: return emptyList()

            return listOfNotNull(RemoveWhenBranchFix(whenEntry))
        }
    }
}