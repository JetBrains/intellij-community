// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.runWriteAction
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
    override fun startInWriteAction(): Boolean = false

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
        if (AddJvmStaticIntention().applicabilityRange(copyDeclarationInCompanion) != null) {
            copyDeclarationInCompanion.addAnnotation(JVM_STATIC_FQ_NAME)
            CodeStyleManager.getInstance(declaration.project).reformat(copyDeclarationInCompanion, true)
        }
        return IntentionPreviewInfo.DIFF
    }

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        fun makeStaticAndReformat(declaration: KtNamedDeclaration, editor: Editor?) {
            val intention = AddJvmStaticIntention()
            if (intention.applicabilityRange(declaration) != null) {
                intention.applyTo(declaration, editor)
                runWriteAction { CodeStyleManager.getInstance(declaration.project).reformat(declaration, true) }
            }
        }

        val containingClass = declaration.containingClassOrObject ?: return
        if (containingClass is KtClass) {
            val moveMemberToCompanionObjectIntention = MoveMemberToCompanionObjectIntention()
            val (conflicts, externalUsages, outerInstanceUsages) =
                moveMemberToCompanionObjectIntention.retrieveConflictsAndUsages(project, editor, declaration, containingClass)
                    ?: return

            project.checkConflictsInteractively(conflicts) {
                ApplicationManagerEx.getApplicationEx().runWriteActionWithNonCancellableProgressInDispatchThread(
                    KotlinBundle.message("making.member.static"), project, null
                ) {
                    val movedDeclaration = moveMemberToCompanionObjectIntention.doMove(
                        it, declaration, externalUsages, outerInstanceUsages, editor
                    )
                    makeStaticAndReformat(movedDeclaration, editor)
                }
            }
        } else makeStaticAndReformat(declaration, editor)
    }

    override fun getText(): String = KotlinBundle.message("make.member.static.quickfix", declaration.name ?: "")

    override fun getFamilyName(): String = text

    companion object {
        private val JVM_STATIC_FQ_NAME = FqName("kotlin.jvm.JvmStatic")
    }
}