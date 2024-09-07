// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.psiUtil.containingClass

class AssignToPropertyFix(
    element: KtNameReferenceExpression,
    private val hasSingleImplicitReceiver: Boolean,
) : KotlinQuickFixAction<KtNameReferenceExpression>(element) {
    override fun getText() = KotlinBundle.message("fix.assign.to.property")

    override fun getFamilyName() = text

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element ?: return
        val psiFactory = KtPsiFactory(project)
        if (hasSingleImplicitReceiver) {
            element.replace(psiFactory.createExpressionByPattern("this.$0", element))
        } else {
            element.containingClass()?.name?.let {
                element.replace(psiFactory.createExpressionByPattern("this@$0.$1", it, element))
            }
        }
    }
}
