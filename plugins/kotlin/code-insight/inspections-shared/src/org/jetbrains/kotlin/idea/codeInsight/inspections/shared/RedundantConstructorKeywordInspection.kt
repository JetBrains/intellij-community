// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.*
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.primaryConstructorVisitor
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

class RedundantConstructorKeywordInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return primaryConstructorVisitor { constructor ->
            val constructorKeyword = constructor.getConstructorKeyword()
            if (constructor.containingClassOrObject is KtClass &&
                constructorKeyword != null &&
                constructor.modifierList == null
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
}

private class RemoveRedundantConstructorFix : LocalQuickFix {
    override fun getName() = KotlinBundle.message("remove.redundant.constructor.keyword.fix.text")

    override fun getFamilyName() = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val constructor = descriptor.psiElement as? KtPrimaryConstructor ?: return
        constructor.removeRedundantConstructorKeywordAndSpace()
    }
}
