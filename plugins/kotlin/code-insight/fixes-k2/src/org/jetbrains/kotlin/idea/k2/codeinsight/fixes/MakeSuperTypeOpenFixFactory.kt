// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.analysis.api.symbols.psiSafe
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.AddModifierFix
import org.jetbrains.kotlin.idea.refactoring.canRefactorElement
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtTypeReference

object MakeSuperTypeOpenFixFactory {

    val makeSuperTypeOpenFixFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KtFirDiagnostic.FinalSupertype ->
        createQuickFixes(diagnostic)
    }

    val makeUpperBoundOpenFixFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KtFirDiagnostic.FinalUpperBound ->
        createQuickFixes(diagnostic)
    }

    context(KtAnalysisSession)
    private fun createQuickFixes(
        diagnostic: KtFirDiagnostic<KtTypeReference>,
    ): List<AddModifierFix> {
        val typeRef = diagnostic.psi as? KtTypeReference
            ?: return emptyList()

        val superType = typeRef.getKtType().expandedClassSymbol?.psiSafe<KtClassOrObject>()
            ?: return emptyList()

        if (!superType.canRefactorElement())
            return emptyList()

        return listOfNotNull(
            AddModifierFix.createIfApplicable(superType, KtTokens.OPEN_KEYWORD),
        )
    }
}
