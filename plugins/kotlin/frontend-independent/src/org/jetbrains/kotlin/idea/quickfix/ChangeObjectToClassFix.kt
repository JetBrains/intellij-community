// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtPsiFactory

class ChangeObjectToClassFix(element: KtObjectDeclaration) : KotlinQuickFixAction<KtObjectDeclaration>(element) {
    override fun getText(): String = KotlinBundle.message("fix.change.object.to.class")

    override fun getFamilyName(): String = text

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val objectDeclaration = element ?: return
        val psiFactory = KtPsiFactory(project)
        objectDeclaration.getObjectKeyword()?.replace(psiFactory.createClassKeyword())
        objectDeclaration.replace(psiFactory.createClass(objectDeclaration.text))
    }
}
