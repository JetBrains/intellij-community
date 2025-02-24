// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.core.implicitModality
import org.jetbrains.kotlin.idea.quickfix.RemoveModifierFix
import org.jetbrains.kotlin.psi.declarationVisitor
import org.jetbrains.kotlin.psi.psiUtil.modalityModifier

class RedundantModalityModifierInspection : AbstractKotlinInspection(), CleanupLocalInspectionTool {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
        return declarationVisitor { declaration ->
            val modalityModifier = declaration.modalityModifier() ?: return@declarationVisitor
            val modalityModifierType = modalityModifier.node.elementType
            val implicitModality = declaration.implicitModality()

            if (modalityModifierType != implicitModality) return@declarationVisitor

            holder.registerProblem(
                modalityModifier,
                KotlinBundle.message("redundant.modality.modifier"),
                IntentionWrapper(RemoveModifierFix(declaration, implicitModality, isRedundant = true))
            )
        }
    }
}
