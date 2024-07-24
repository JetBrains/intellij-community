// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.CodeInsightUtilCore
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtWhenEntry
import org.jetbrains.kotlin.psi.KtWhenExpression

class MoveWhenElseBranchFix(element: KtWhenExpression) : KotlinQuickFixAction<KtWhenExpression>(element) {
    override fun getFamilyName() = KotlinBundle.message("move.else.branch.to.the.end")

    override fun getText() = familyName

    override fun isAvailable(project: Project, editor: Editor?, file: KtFile): Boolean {
        val element = element ?: return false
        return KtPsiUtil.checkWhenExpressionHasSingleElse(element)
    }

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element ?: return
        val entries = element.entries
        val lastEntry = entries.lastOrNull() ?: return
        val elseEntry = entries.singleOrNull { it.isElse } ?: return

        val cursorOffset = editor!!.caretModel.offset - elseEntry.textOffset

        val insertedBranch = element.addAfter(elseEntry, lastEntry) as KtWhenEntry
        elseEntry.delete()
        val insertedWhenEntry = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(insertedBranch) ?: return

        editor.caretModel.moveToOffset(insertedWhenEntry.textOffset + cursorOffset)
    }

}
