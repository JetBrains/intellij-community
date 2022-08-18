// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.ide.DataManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.refactoring.rename.RenameHandlerRegistry
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

class RenameUnderscoreFix(declaration: KtDeclaration) : KotlinQuickFixAction<KtDeclaration>(declaration) {
    override fun startInWriteAction(): Boolean = false

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        if (editor == null) return
        val dataContext = DataManager.getInstance().getDataContext(editor.component)
        val renameHandler = RenameHandlerRegistry.getInstance().getRenameHandler(dataContext)
        renameHandler?.invoke(project, arrayOf(element), dataContext)
    }

    override fun isAvailable(project: Project, editor: Editor?, file: KtFile): Boolean {
        return editor != null
    }

    override fun getText(): String = KotlinBundle.message("rename.identifier.fix.text")
    override fun getFamilyName(): String = text

    companion object : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val declaration = diagnostic.psiElement.getNonStrictParentOfType<KtDeclaration>() ?: return null
            if (diagnostic.psiElement == (declaration as? PsiNameIdentifierOwner)?.nameIdentifier) {
                return RenameUnderscoreFix(declaration)
            }
            return null
        }
    }
}