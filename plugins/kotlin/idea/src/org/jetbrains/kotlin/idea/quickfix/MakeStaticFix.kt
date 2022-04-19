// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.intentions.AddJvmStaticIntention
import org.jetbrains.kotlin.idea.intentions.MoveMemberToCompanionObjectIntention
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

class MakeStaticFix(private val declaration: KtNamedDeclaration) : KotlinQuickFixAction<KtNamedDeclaration>(declaration) {
    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val containingClass = declaration.containingClassOrObject ?: return
        val newDeclaration = if (containingClass is KtClass) {
            MoveMemberToCompanionObjectIntention().applyTo(declaration, editor)
            containingClass.companionObjects.flatMap { it.declarations }.find { it.textMatches(declaration) } as KtNamedDeclaration
        } else declaration
        AddJvmStaticIntention().applyTo(newDeclaration, editor)
        CodeStyleManager.getInstance(project).reformat(newDeclaration, true)
    }

    override fun getText(): String = KotlinBundle.message("make.static.quickfix", declaration.name ?: "")

    override fun getFamilyName(): String = text
}