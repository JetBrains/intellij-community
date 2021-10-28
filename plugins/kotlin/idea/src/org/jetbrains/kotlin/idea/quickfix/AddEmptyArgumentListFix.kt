// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class AddEmptyArgumentListFix(callExpression: KtCallExpression) : KotlinQuickFixAction<KtCallExpression>(callExpression) {
    override fun getText() = KotlinBundle.message("add.empty.argument.list")

    override fun getFamilyName() = text

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val callExpression = this.element ?: return
        val calleeExpression = callExpression.calleeExpression ?: return
        val emptyArgumentList = KtPsiFactory(callExpression).createValueArgumentListByPattern("()")
        callExpression.addAfter(emptyArgumentList, calleeExpression)
    }

    companion object : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic) =
            diagnostic.psiElement.parent.safeAs<KtCallExpression>()?.let { AddEmptyArgumentListFix(it) }
    }
}