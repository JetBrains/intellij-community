// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ModCommandAction
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.symbols.psiSafe
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.AddModifierFixMpp
import org.jetbrains.kotlin.idea.refactoring.canRefactorElement
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtTypeReference

internal object MakeSuperTypeOpenFixFactory {

    val makeSuperTypeOpenFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.FinalSupertype ->
        createQuickFixes(diagnostic)
    }

    val makeUpperBoundOpenFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.FinalUpperBound ->
        createQuickFixes(diagnostic)
    }

    private fun KaSession.createQuickFixes(
        diagnostic: KaFirDiagnostic<KtElement>,
    ): List<ModCommandAction> {
        val typeRef = diagnostic.psi as? KtTypeReference
            ?: return emptyList()

        val superType = typeRef.type.expandedSymbol?.psiSafe<KtClassOrObject>()
            ?: return emptyList()

        if (!superType.canRefactorElement())
            return emptyList()

        return listOfNotNull(
            AddModifierFixMpp.createIfApplicable(superType, KtTokens.OPEN_KEYWORD),
        )
    }
}
