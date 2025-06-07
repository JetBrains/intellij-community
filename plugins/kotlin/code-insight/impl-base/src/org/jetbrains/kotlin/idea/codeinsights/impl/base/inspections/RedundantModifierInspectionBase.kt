// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.inspections

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.tree.TokenSet
import org.jetbrains.kotlin.analysis.api.diagnostics.KaDiagnosticWithPsi
import org.jetbrains.kotlin.idea.base.psi.isRedundant
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinPsiDiagnosticBasedInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtVisitorVoid

abstract class RedundantModifierInspectionBase<DIAGNOSTIC : KaDiagnosticWithPsi<KtModifierListOwner>>(
    private val modifierSet: TokenSet,
) : KotlinPsiDiagnosticBasedInspectionBase<KtModifierListOwner, DIAGNOSTIC, RedundantModifierInspectionBase.ModifierContext>(),
    CleanupLocalInspectionTool {

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitorVoid = object : KtVisitorVoid() {

        override fun visitDeclaration(dcl: KtDeclaration) {
            visitTargetElement(dcl, holder, isOnTheFly)
        }
    }

    data class ModifierContext(val modifier: KtModifierKeywordToken)

    override fun getProblemDescription(element: KtModifierListOwner, context: ModifierContext): String =
        KotlinBundle.message("redundant.0.modifier", context.modifier.value)

    override fun isApplicableByPsi(element: KtModifierListOwner): Boolean = element.modifierList?.getModifier(modifierSet) != null

    protected abstract class RemoveRedundantModifierQuickFixBase(
        private val context: ModifierContext,
    ) : KotlinModCommandQuickFix<KtModifierListOwner>() {

        override fun getName(): String =
            KotlinBundle.message("remove.redundant.0.modifier", context.modifier.value)

        override fun applyFix(
            project: Project,
            element: KtModifierListOwner,
            updater: ModPsiUpdater,
        ) {
            element.removeModifier(context.modifier)
            if (element is KtPrimaryConstructor && element.isRedundant()) {
                element.delete()
            }
        }
    }
}