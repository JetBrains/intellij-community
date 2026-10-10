// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighting

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.model.SymbolPointer
import com.intellij.refactoring.modplan.ModPlanCommandAction
import com.intellij.refactoring.modplan.ModPlanOptionValues
import com.intellij.refactoring.modplan.ModPlanRequest
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteOptions
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteRefactoring
import org.jetbrains.kotlin.psi.KtNamedDeclaration

/** @return the safe delete of [declaration] alone, on the ModPlan engine. Every safe delete fix of a Kotlin declaration is created here. */
internal fun createSafeDeleteFix(declaration: KtNamedDeclaration): LocalQuickFixAndIntentionActionOnPsiElement =
    LocalQuickFixAndIntentionActionOnPsiElement.from(ModPlanSafeDeleteFix(declaration), declaration)

/**
 * The safe delete of [declaration] alone on the ModPlan engine, as a [com.intellij.modcommand.ModCommandAction]. It is not offered for a declaration that the ModPlan safe delete does not support. See [ModPlanCommandAction] for the
 * differences from the legacy flow.
 */
internal class ModPlanSafeDeleteFix(declaration: KtNamedDeclaration) : ModPlanCommandAction(
    ModPlanSafeDeleteRefactoring.getInstance(),
    ModPlanRequest(
        declaration.project,
        listOf(SymbolPointer.of(declaration)),
        ModPlanOptionValues.EMPTY.with(ModPlanSafeDeleteOptions.SEARCH_RELATED, false),
    ),
    declaration,
    declaration.safeDeleteText(),
) {
    override fun getFamilyName(): String = K2HighlightingBundle.message("safe.delete.family")
}
