// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

class AddFunctionBodyFix(element: KtFunction) : KotlinQuickFixAction<KtFunction>(element) {
    override fun getFamilyName() = KotlinBundle.message("fix.add.function.body")
    override fun getText() = familyName

    override fun isAvailable(project: Project, editor: Editor?, file: KtFile): Boolean {
        val element = element ?: return false
        return !element.hasBody()
    }

    public override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element ?: return
        if (!element.hasBody()) {
            element.add(KtPsiFactory(project).createEmptyBody())
        }
    }

    companion object : KotlinSingleIntentionActionFactory() {
        public override fun createAction(diagnostic: Diagnostic): AddFunctionBodyFix? {
            return diagnostic.psiElement.getNonStrictParentOfType<KtFunction>()?.let(::AddFunctionBodyFix)
        }
    }
}
