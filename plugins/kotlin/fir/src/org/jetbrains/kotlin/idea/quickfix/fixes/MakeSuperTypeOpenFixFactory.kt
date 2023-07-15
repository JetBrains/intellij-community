// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix.fixes

import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic.FinalSupertype
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic.FinalUpperBound
import org.jetbrains.kotlin.analysis.api.symbols.psiSafe
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.diagnosticFixFactories
import org.jetbrains.kotlin.idea.quickfix.AddModifierFix
import org.jetbrains.kotlin.idea.refactoring.canRefactorElement
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtTypeReference

object MakeSuperTypeOpenFixFactory {
    val makeSuperTypeOpenFixFactory = diagnosticFixFactories(
        FinalSupertype::class,
        FinalUpperBound::class
    ) { diagnostic ->
        val typeRef = diagnostic.psi as? KtTypeReference ?: return@diagnosticFixFactories emptyList()
        val superType = typeRef.getKtType().expandedClassSymbol?.psiSafe<KtClassOrObject>() ?: return@diagnosticFixFactories emptyList()
        if (!superType.canRefactorElement()) return@diagnosticFixFactories emptyList()
        listOfNotNull(AddModifierFix.createIfApplicable(superType, KtTokens.OPEN_KEYWORD))
    }
}
