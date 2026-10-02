// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ConvertToPositionalDestructuringFix
import org.jetbrains.kotlin.psi.KtDestructuringDeclaration
import org.jetbrains.kotlin.psi.KtDestructuringDeclarationEntry
import org.jetbrains.kotlin.resolve.calls.util.isSingleUnderscore

internal object DestructuringToPositionalFormFactory {
    val convertToPositionalFormOnShortFormNameMismatch =
        KotlinQuickFixFactory.ModCommandBased<KaFirDiagnostic.DestructuringShortFormNameMismatch> { createFix(it.psi) }

    val convertToPositionalFormOnShortFormUnderscore =
        KotlinQuickFixFactory.ModCommandBased<KaFirDiagnostic.DestructuringShortFormUnderscore> { createFix(it.psi) }

    val convertToPositionalFormOnShortUnderscoreWithoutRename =
        KotlinQuickFixFactory.ModCommandBased<KaFirDiagnostic.NameBasedDestructuringUnderscoreWithoutRenaming> { createFix(it.psi) }

    val convertToPositionalFormOnShortFormNonDataClass =
        KotlinQuickFixFactory.ModCommandBased<KaFirDiagnostic.DestructuringShortFormOfNonDataClass> { createFix(it.psi) }

    private fun createFix(psi: PsiElement): List<ConvertToPositionalDestructuringFix> {
        val entry = psi as? KtDestructuringDeclarationEntry ?: return emptyList()
        val declaration = entry.parent as? KtDestructuringDeclaration ?: return emptyList()

        if (declaration.entries.all { it.isSingleUnderscore }) return emptyList()

        return listOf(ConvertToPositionalDestructuringFix(declaration))
    }
}
