// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.intentions.AddJvmStaticIntention
import org.jetbrains.kotlin.idea.intentions.MoveMemberToCompanionObjectIntention
import org.jetbrains.kotlin.idea.refactoring.checkConflictsInteractively
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

class MakeMemberStaticFix(private val declaration: KtNamedDeclaration) : KotlinQuickFixAction<KtNamedDeclaration>(declaration) {
    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        fun makeStaticAndReformat(declaration: KtNamedDeclaration) {
            val makeStaticIntention = AddJvmStaticIntention()
            if (makeStaticIntention.applicabilityRange(declaration) != null) AddJvmStaticIntention().applyTo(declaration, editor)
            CodeStyleManager.getInstance(declaration.project).reformat(declaration, true)
        }

        val containingClass = declaration.containingClassOrObject ?: return
        if (containingClass is KtClass) {
            val moveMemberToCompanionObjectIntention = MoveMemberToCompanionObjectIntention()
            val (conflicts, externalUsages, outerInstanceUsages) =
                moveMemberToCompanionObjectIntention.retrieveConflictsAndUsages(project, editor, declaration, containingClass)
                    ?: return

            project.checkConflictsInteractively(conflicts) {
                var movedDeclaration: KtNamedDeclaration? = null
                ApplicationManagerEx.getApplicationEx().runWriteActionWithNonCancellableProgressInDispatchThread(
                    KotlinBundle.message("moving.to.companion.object"), project, null
                ) {
                    movedDeclaration = moveMemberToCompanionObjectIntention.doMove(
                        it, declaration, externalUsages, outerInstanceUsages, editor
                    )

                }
                movedDeclaration?.let { makeStaticAndReformat(it) }
            }
        } else makeStaticAndReformat(declaration)
    }

    override fun getText(): String = KotlinBundle.message("make.member.static.quickfix", declaration.name ?: "")

    override fun getFamilyName(): String = text
}