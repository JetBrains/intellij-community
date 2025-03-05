// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsight.utils.canBeCompletelyDeleted
import org.jetbrains.kotlin.idea.codeinsight.utils.isRedundantSetter
import org.jetbrains.kotlin.idea.codeinsight.utils.removeRedundantSetter
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.propertyAccessorVisitor
import org.jetbrains.kotlin.psi.psiUtil.startOffset

internal class RedundantSetterInspection : AbstractKotlinInspection(), CleanupLocalInspectionTool {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
        return propertyAccessorVisitor { accessor ->
            val rangeInElement = accessor.namePlaceholder.textRange?.shiftRight(-accessor.startOffset) ?: return@propertyAccessorVisitor
            if (accessor.isRedundantSetter()) {
                val canBeCompletelyDeleted = accessor.canBeCompletelyDeleted()
                val messageKey = if (canBeCompletelyDeleted) "redundant.setter" else "redundant.setter.body"
                holder.registerProblem(
                    accessor,
                    KotlinBundle.message(messageKey),
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                    rangeInElement,
                    RemoveRedundantSetterFix(canBeCompletelyDeleted)
                )
            }
        }
    }
}

private class RemoveRedundantSetterFix(private val canBeCompletelyDeleted: Boolean) : PsiUpdateModCommandQuickFix() {

    override fun getFamilyName(): @IntentionFamilyName String {
        val key = if (canBeCompletelyDeleted) "remove.redundant.setter.fix.text" else "remove.redundant.setter.body.fix.text"
        return KotlinBundle.message(key)
    }

    override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
        val accessor = element as? KtPropertyAccessor ?: return
        removeRedundantSetter(accessor)
    }
}