// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinPsiOnlyQuickFixAction
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.PsiElementSuitabilityCheckers
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.QuickFixesPsiBasedFactory
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtValVarKeywordOwner
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

class RemoveValVarFromParameterFix(element: KtValVarKeywordOwner) : KotlinPsiOnlyQuickFixAction<KtValVarKeywordOwner>(element) {
    override fun getFamilyName() = KotlinBundle.message("remove.val.or.var.from.parameter")

    override fun getText(): String {
        val varOrVal = element?.valOrVarKeyword?.text ?: return familyName
        return KotlinBundle.message("remove.0.from.parameter", varOrVal)
    }

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        element?.valOrVarKeyword?.delete()
    }

    companion object : QuickFixesPsiBasedFactory<PsiElement>(PsiElement::class, PsiElementSuitabilityCheckers.ALWAYS_SUITABLE) {
        override fun doCreateQuickFix(psiElement: PsiElement): List<IntentionAction> {
            val owner = psiElement.getNonStrictParentOfType<KtValVarKeywordOwner>() ?: return emptyList()
            return listOf(RemoveValVarFromParameterFix(owner))
        }
    }
}
