// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.psi.KtDeclarationWithBody
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory

class AddReturnToLastExpressionInFunctionFix(element: KtDeclarationWithBody) : KotlinQuickFixAction<KtDeclarationWithBody>(element) {

    override fun getText() = KotlinBundle.message("fix.add.return.last.expression")
    override fun getFamilyName() = text

    override fun isAvailable(project: Project, editor: Editor?, file: KtFile): Boolean = element is KtNamedFunction

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element as? KtNamedFunction ?: return
        val last = element.bodyBlockExpression?.statements?.lastOrNull() ?: return
        last.replace(KtPsiFactory(project).createExpression("return ${last.text}"))
    }
}
