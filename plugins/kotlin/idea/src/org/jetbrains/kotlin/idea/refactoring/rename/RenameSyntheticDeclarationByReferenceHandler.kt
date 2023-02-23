// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.rename.RenameHandler
import com.intellij.refactoring.util.CommonRefactoringUtil
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.resolve.calls.tower.isSynthesized
import org.jetbrains.kotlin.resolve.sam.SamConstructorDescriptor

class RenameSyntheticDeclarationByReferenceHandler : RenameHandler {
    override fun isAvailableOnDataContext(dataContext: DataContext): Boolean {
        val file = CommonDataKeys.PSI_FILE.getData(dataContext) ?: return false
        val editor = CommonDataKeys.EDITOR.getData(dataContext) ?: return false
        val refExpression = file.findElementForRename<KtSimpleNameExpression>(editor.caretModel.offset) ?: return false
        val descriptor = refExpression.resolveToCall()?.resultingDescriptor ?: return false
        // SamConstructorDescriptor is synthetic but it can be renamed
        return descriptor !is SamConstructorDescriptor && descriptor.isSynthesized
    }

    override fun isRenaming(dataContext: DataContext) = isAvailableOnDataContext(dataContext)

    override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext?) {
        CommonRefactoringUtil.showErrorHint(
            project,
            editor,
            KotlinBundle.message("text.rename.is.not.applicable.to.synthetic.declarations"),
            RefactoringBundle.message("rename.title"),
            null
        )
    }

    override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) {
        // Do nothing: this method is not called from editor
    }
}
