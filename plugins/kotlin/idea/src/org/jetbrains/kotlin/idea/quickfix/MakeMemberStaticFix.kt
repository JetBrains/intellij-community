// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeInsight.intentions.shared.AddJvmStaticIntention
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.intentions.MoveMemberToCompanionObjectIntention
import org.jetbrains.kotlin.idea.util.addAnnotation
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

class MakeMemberStaticFix(declaration: KtNamedDeclaration) : KotlinQuickFixAction<KtNamedDeclaration>(declaration) {
    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        var declaration = element ?: return
        if (declaration is KtClass) {
            if (declaration.hasModifier(KtTokens.INNER_KEYWORD)) declaration.removeModifier(KtTokens.INNER_KEYWORD)
        } else {
            val containingClass = declaration.containingClassOrObject ?: return
            if (containingClass is KtClass) {
                val moveMemberToCompanionObjectIntention = MoveMemberToCompanionObjectIntention()
                declaration = moveMemberToCompanionObjectIntention.doMove(
                    EmptyProgressIndicator(), declaration, listOf(), listOf(), editor
                )
            }
        }
        if (AddJvmStaticIntention().applicabilityRange(declaration) != null) {
            declaration.addAnnotation(JVM_STATIC_FQ_NAME)
            CodeStyleManager.getInstance(project).reformat(declaration, true)
        }
    }

    override fun getText(): String = KotlinBundle.message("make.member.static.quickfix", element?.name ?: "")

    override fun getFamilyName(): String = text

    companion object {
        private val JVM_STATIC_FQ_NAME = FqName("kotlin.jvm.JvmStatic")
    }
}