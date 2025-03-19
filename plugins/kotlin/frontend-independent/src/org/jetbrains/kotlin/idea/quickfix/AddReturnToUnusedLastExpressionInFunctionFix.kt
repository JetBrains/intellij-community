// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory

class AddReturnToUnusedLastExpressionInFunctionFix(element: KtElement) : KotlinQuickFixAction<KtElement>(element) {

    override fun getText(): String = KotlinBundle.message("fix.add.return.before.expression")
    override fun getFamilyName(): String = text

    override fun isAvailable(project: Project, editor: Editor?, file: KtFile): Boolean = element != null

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element ?: return
        element.replace(KtPsiFactory(project).createExpression("return ${element.text}"))
    }
}
