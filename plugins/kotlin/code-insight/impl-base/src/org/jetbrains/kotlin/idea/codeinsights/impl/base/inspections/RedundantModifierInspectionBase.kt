// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.inspections

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.tree.TokenSet
import org.jetbrains.kotlin.analysis.api.diagnostics.KtDiagnosticWithPsi
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.AbstractKotlinApplicableModCommandDiagnosticInspectionWithContext
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtVisitorVoid

abstract class RedundantModifierInspectionBase<DIAGNOSTIC : KtDiagnosticWithPsi<KtModifierListOwner>>(
    private val modifierSet: TokenSet,
) : AbstractKotlinApplicableModCommandDiagnosticInspectionWithContext<KtModifierListOwner, DIAGNOSTIC, RedundantModifierInspectionBase.ModifierContext>(),
    CleanupLocalInspectionTool {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
        return object : KtVisitorVoid() {
            override fun visitDeclaration(dcl: KtDeclaration) {
                visitTargetElement(dcl, holder, isOnTheFly)
            }
        }
    }
    class ModifierContext(val modifier: KtModifierKeywordToken)

    override fun getProblemDescription(element: KtModifierListOwner, context: ModifierContext): String =
        KotlinBundle.message("redundant.0.modifier", context.modifier.value)

    override fun getActionName(element: KtModifierListOwner, context: ModifierContext): String =
        KotlinBundle.message("remove.redundant.0.modifier", context.modifier.value)

    override fun isApplicableByPsi(element: KtModifierListOwner): Boolean = element.modifierList?.getModifier(modifierSet) != null

    override fun apply(element: KtModifierListOwner, context: ModifierContext, project: Project, updater: ModPsiUpdater) {
        element.removeModifier(context.modifier)
    }
}