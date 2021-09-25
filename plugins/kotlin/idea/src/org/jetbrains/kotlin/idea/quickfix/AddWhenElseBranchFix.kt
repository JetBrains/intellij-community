// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.CodeInsightUtilCore
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtWhenEntry
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

class AddWhenElseBranchFix(element: KtWhenExpression) : KotlinQuickFixAction<KtWhenExpression>(element) {
    override fun getFamilyName() = KotlinBundle.message("fix.add.else.branch.when")
    override fun getText() = familyName

    override fun isAvailable(project: Project, editor: Editor?, file: KtFile): Boolean {
        val element = element ?: return false
        return element.closeBrace != null
    }

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element ?: return
        val psiFactory = KtPsiFactory(file)
        val entry = psiFactory.createWhenEntry("else ->")
        val whenCloseBrace = element.closeBrace ?: error("isAvailable should check if close brace exist")
        val insertedWhenEntry =
            CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(element.addBefore(entry, whenCloseBrace)) as KtWhenEntry
        val endOffset = insertedWhenEntry.endOffset
        editor?.document?.insertString(endOffset, " ")
        editor?.caretModel?.moveToOffset(endOffset + 1)
    }

    companion object : KotlinSingleIntentionActionFactory() {
        public override fun createAction(diagnostic: Diagnostic): AddWhenElseBranchFix? {
            return diagnostic.psiElement.getNonStrictParentOfType<KtWhenExpression>()?.let(::AddWhenElseBranchFix)
        }
    }
}
