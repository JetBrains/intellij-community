// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.idea.base.psi.addModifierKeyword
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.declarationVisitor
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifier

abstract class ProtectedInFinalInspectionBase : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return declarationVisitor(fun(declaration) {
            val visibilityModifier = declaration.visibilityModifier() ?: return
            if (declaration.hasModifier(KtTokens.PROTECTED_KEYWORD)) {
                val parentClass = declaration.getParentOfType<KtClass>(true) ?: return
                if (isApplicable(parentClass, declaration)) {
                    val quickFixes = buildList {
                        add(MakePrivateFix())
                        if (!parentClass.isEffectivelyFinal()) {
                            add(MakeOpenFix())
                        }
                    }
                    holder.registerProblem(
                        visibilityModifier,
                        KotlinBundle.message("protected.visibility.is.effectively.private.in.a.final.class"),
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                        *quickFixes.toTypedArray(),
                    )
                }
            }
        })
    }

    private fun KtClass.isEffectivelyFinal(): Boolean {
        return hasModifier(KtTokens.DATA_KEYWORD) || hasModifier(KtTokens.ENUM_KEYWORD) || hasModifier(KtTokens.VALUE_KEYWORD)
    }
    protected abstract fun isApplicable(parentClass: KtClass, declaration: KtDeclaration): Boolean

    private class MakePrivateFix : LocalQuickFix {
        override fun getFamilyName(): String = KotlinBundle.message("make.private.fix.text")

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val modifierListOwner = descriptor.psiElement.getParentOfType<KtModifierListOwner>(true)
                ?: throw IllegalStateException("Can't find modifier list owner for modifier")
            modifierListOwner.addModifierKeyword(KtTokens.PRIVATE_KEYWORD)
        }
    }

    private class MakeOpenFix : LocalQuickFix {
        override fun getFamilyName(): String = KotlinBundle.message("make.open.fix.text")

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val modifierListOwner = descriptor.psiElement.getParentOfType<KtModifierListOwner>(true)
                ?: throw IllegalStateException("Can't find modifier list owner for modifier")
            val parentClass = modifierListOwner.getParentOfType<KtClass>(true) ?: return
            parentClass.addModifierKeyword(KtTokens.OPEN_KEYWORD)
        }
    }
}