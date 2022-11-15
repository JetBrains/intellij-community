// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.core.getOrCreateCompanionObject
import org.jetbrains.kotlin.idea.intentions.AddJvmStaticIntention
import org.jetbrains.kotlin.idea.intentions.MoveMemberToCompanionObjectIntention
import org.jetbrains.kotlin.idea.refactoring.checkConflictsInteractively
import org.jetbrains.kotlin.idea.util.addAnnotation
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

class MakeMemberStaticFix(private val declaration: KtNamedDeclaration) : KotlinQuickFixAction<KtNamedDeclaration>(declaration) {
    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        val copyDeclaration = PsiTreeUtil.findSameElementInCopy(declaration, file)
        val containingClass = copyDeclaration.containingClassOrObject ?: return IntentionPreviewInfo.EMPTY
        val copyDeclarationInCompanion = if (containingClass is KtClass) {
            val companionObject = containingClass.getOrCreateCompanionObject()
            MoveMemberToCompanionObjectIntention.removeModifiers(copyDeclaration)
            val newDeclaration = companionObject.addDeclaration(copyDeclaration)
            copyDeclaration.delete()
            newDeclaration
        } else copyDeclaration
        copyDeclarationInCompanion.addAnnotation(JVM_STATIC_FQ_NAME)
        return IntentionPreviewInfo.DIFF
    }

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        fun makeStaticAndReformat(declaration: KtNamedDeclaration, editor: Editor?) {
            AddJvmStaticIntention().applyTo(declaration, editor)
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
                movedDeclaration?.let { makeStaticAndReformat(it, editor) }
            }
        } else makeStaticAndReformat(declaration, editor)
    }



    override fun getText(): String = KotlinBundle.message("make.member.static.quickfix", declaration.name ?: "")

    override fun getFamilyName(): String = text

    companion object {
        private val JVM_STATIC_FQ_NAME = FqName("kotlin.jvm.JvmStatic")
    }
}