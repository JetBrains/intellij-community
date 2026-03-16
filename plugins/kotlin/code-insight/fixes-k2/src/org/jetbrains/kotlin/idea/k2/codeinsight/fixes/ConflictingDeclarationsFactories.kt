// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ModCommandAction
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory

internal object ConflictingDeclarationsFactories {
    val conflictingOverloads = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ConflictingOverloads ->
        createShowConflictingDeclarationsAction(diagnostic.psi, diagnostic.conflictingOverloads)
    }

    val redeclaration = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.Redeclaration ->
        createShowConflictingDeclarationsAction(diagnostic.psi, diagnostic.conflictingDeclarations)
    }

    val classifierRedeclaration = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ClassifierRedeclaration ->
        createShowConflictingDeclarationsAction(diagnostic.psi, diagnostic.conflictingDeclarations)
    }

    context(_: KaSession)
    private fun createShowConflictingDeclarationsAction(
        originalDeclaration: PsiElement,
        conflictingSymbols: Collection<KaSymbol>
    ): List<ModCommandAction> {
        if (originalDeclaration !is NavigatablePsiElement) return emptyList()

        val conflictingDeclarations = conflictingSymbols
            .mapNotNull { it.psi as? NavigatablePsiElement }
            .filterNot { it == originalDeclaration } // we want to exclude the original element itself in cases when it is duplicated here

        return listOf(ShowConflictingDeclarationsAction(originalDeclaration, conflictingDeclarations))
    }
}
