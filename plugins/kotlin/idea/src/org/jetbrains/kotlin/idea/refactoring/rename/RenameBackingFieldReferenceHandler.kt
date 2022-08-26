// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringBundle
import org.jetbrains.kotlin.descriptors.impl.SyntheticFieldDescriptor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.core.surroundWith.KotlinSurrounderUtils
import org.jetbrains.kotlin.psi.KtSimpleNameExpression

class RenameBackingFieldReferenceHandler : KotlinVariableInplaceRenameHandler() {
    override fun isAvailable(element: PsiElement?, editor: Editor, file: PsiFile): Boolean {
        val refExpression = file.findElementForRename<KtSimpleNameExpression>(editor.caretModel.offset) ?: return false
        if (refExpression.text != "field") return false
        return refExpression.resolveToCall()?.resultingDescriptor is SyntheticFieldDescriptor
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?, dataContext: DataContext) {
        editor?.let {
            KotlinSurrounderUtils.showErrorHint(
                project,
                editor,
                KotlinBundle.message("text.rename.not.applicable.to.backing.field.reference"),
                RefactoringBundle.message("rename.title"),
                null
            )
        }
    }

    override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext) {
        // Do nothing: this method is called not from editor
    }
}
