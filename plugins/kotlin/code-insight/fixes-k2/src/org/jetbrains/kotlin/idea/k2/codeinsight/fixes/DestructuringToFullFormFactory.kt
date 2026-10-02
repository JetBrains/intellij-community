// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsight.utils.NameBasedDestructuringForm
import org.jetbrains.kotlin.idea.codeinsight.utils.applyNameBasedDestructuringForm
import org.jetbrains.kotlin.idea.codeinsight.utils.extractPrimaryParameters
import org.jetbrains.kotlin.idea.codeinsight.utils.isPositionalDestructuringType
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtDestructuringDeclaration
import org.jetbrains.kotlin.psi.KtDestructuringDeclarationEntry

internal object DestructuringToFullFormFactory {
    val convertToFullFormOnShortFormNameMismatch =
        KotlinQuickFixFactory.ModCommandBased<KaFirDiagnostic.DestructuringShortFormNameMismatch> { createFix(it.psi) }

    val convertToFullFormOnShortFormUnderscore =
        KotlinQuickFixFactory.ModCommandBased<KaFirDiagnostic.DestructuringShortFormUnderscore> { createFix(it.psi) }

    val convertToFullFormOnShortUnderscoreWithoutRename =
        KotlinQuickFixFactory.ModCommandBased<KaFirDiagnostic.NameBasedDestructuringUnderscoreWithoutRenaming> { createFix(it.psi) }

    context(_: KaSession)
    private fun createFix(psi: PsiElement): List<ConvertNameBasedDestructuringToFullFormFix> {
        val entry = psi as? KtDestructuringDeclarationEntry ?: return emptyList()
        val declaration = entry.parent as? KtDestructuringDeclaration ?: return emptyList()

        if (declaration.isPositionalDestructuringType()) return emptyList()
        val propertyNames = extractPrimaryParameters(declaration)
            ?.take(declaration.entries.size)
            ?.map { it.name }
            ?: return emptyList()
        return listOf(ConvertNameBasedDestructuringToFullFormFix(propertyNames, declaration))
    }

    private class ConvertNameBasedDestructuringToFullFormFix(val propertyNames: List<Name>, declaration: KtDestructuringDeclaration) :
        KotlinPsiUpdateModCommandAction.ElementContextless<KtDestructuringDeclaration>(declaration) {
        override fun getFamilyName(): String = KotlinBundle.message("convert.to.full.name.based.form.destructing")
        override operator fun invoke(
            context: ActionContext,
            element: KtDestructuringDeclaration,
            updater: ModPsiUpdater
        ) {
            element.applyNameBasedDestructuringForm(NameBasedDestructuringForm(propertyNames, positionBased = false, useFullForm = true))
        }
    }
}
