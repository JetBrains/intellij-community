// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.psi.PsiNameIdentifierOwner
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.RenameUnderscoreFix
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

internal object RenameUnderscoreFixFactory {

    val renameUnderscore = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.UnderscoreIsReserved ->
        val psiElement = diagnostic.psi
        val declaration = psiElement.getNonStrictParentOfType<KtDeclaration>() ?: return@ModCommandBased emptyList()
        val fixes = if ((declaration as? PsiNameIdentifierOwner)?.nameIdentifier != null) {
            listOf(RenameUnderscoreFix(declaration))
        } else {
            emptyList()
        }
        return@ModCommandBased fixes
    }
}