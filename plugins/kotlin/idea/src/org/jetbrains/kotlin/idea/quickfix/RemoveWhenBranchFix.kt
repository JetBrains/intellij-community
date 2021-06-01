// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtWhenEntry
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

class RemoveWhenBranchFix(element: KtWhenEntry) : KotlinQuickFixAction<KtWhenEntry>(element) {
    override fun getFamilyName() = if (element?.isElse == true) {
        KotlinBundle.message("remove.else.branch")
    } else {
        KotlinBundle.message("remove.branch")
    }

    override fun getText() = familyName

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        element?.delete()
    }

    companion object : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): RemoveWhenBranchFix? {
            return when (diagnostic.factory) {
                Errors.REDUNDANT_ELSE_IN_WHEN ->
                    (diagnostic.psiElement as? KtWhenEntry)?.let { RemoveWhenBranchFix(it) }
                Errors.SENSELESS_NULL_IN_WHEN ->
                    diagnostic.psiElement.getStrictParentOfType<KtWhenEntry>()?.let { RemoveWhenBranchFix(it) }
                else ->
                    null
            }
        }
    }
}
