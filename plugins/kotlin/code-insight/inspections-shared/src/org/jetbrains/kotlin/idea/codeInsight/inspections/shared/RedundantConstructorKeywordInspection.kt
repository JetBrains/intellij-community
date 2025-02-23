// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.primaryConstructorVisitor
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.siblings

internal class RedundantConstructorKeywordInspection : AbstractKotlinInspection(), CleanupLocalInspectionTool {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return primaryConstructorVisitor { constructor ->
            val constructorKeyword = constructor.getConstructorKeyword()
            if (constructor.containingClassOrObject is KtClass &&
                constructorKeyword != null &&
                constructor.modifierList == null &&
                !constructor.hasPreviousComment()
            ) {
                holder.registerProblem(
                    constructor,
                    KotlinBundle.message("redundant.constructor.keyword"),
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                    constructorKeyword.textRangeInParent,
                    RemoveRedundantConstructorFix()
                )
            }
        }
    }

    private fun KtPrimaryConstructor.hasPreviousComment(): Boolean =
        siblings(forward = false, withItself = false).takeWhile { it is PsiComment || it is PsiWhiteSpace }.any { it is PsiComment }
}

private class RemoveRedundantConstructorFix : PsiUpdateModCommandQuickFix() {
    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("remove.redundant.constructor.keyword.fix.text")

    override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
        val constructor = element as? KtPrimaryConstructor ?: return
        constructor.removeRedundantConstructorKeywordAndSpace()
    }
}
