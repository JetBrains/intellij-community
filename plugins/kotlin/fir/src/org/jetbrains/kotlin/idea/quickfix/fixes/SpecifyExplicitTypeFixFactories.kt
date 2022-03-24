/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.quickfix.fixes

import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.idea.fir.api.fixes.diagnosticFixFactory
import org.jetbrains.kotlin.idea.fir.api.fixes.withInput
import org.jetbrains.kotlin.idea.fir.intentions.declarations.HLSpecifyExplicitTypeForCallableDeclarationIntention
import org.jetbrains.kotlin.idea.fir.intentions.declarations.HLSpecifyExplicitTypeForCallableDeclarationIntention.Companion.getTypeInfo
import org.jetbrains.kotlin.psi.KtCallableDeclaration

object SpecifyExplicitTypeFixFactories {

    val ambiguousAnonymousTypeInferred = diagnosticFixFactory(
        KtFirDiagnostic.AmbiguousAnonymousTypeInferred::class,
        HLSpecifyExplicitTypeForCallableDeclarationIntention.applicator
    ) { diagnostic ->
        val declaration = diagnostic.psi as? KtCallableDeclaration ?: return@diagnosticFixFactory emptyList()
        listOf(declaration withInput getTypeInfo(declaration))
    }

    val noExplicitReturnTypeInApiMode =
        diagnosticFixFactory(
            KtFirDiagnostic.NoExplicitReturnTypeInApiMode::class,
            HLSpecifyExplicitTypeForCallableDeclarationIntention.applicator
        ) { diagnostic ->
            val callableDeclaration = diagnostic.psi as? KtCallableDeclaration ?: return@diagnosticFixFactory emptyList()
            listOf(callableDeclaration withInput getTypeInfo(callableDeclaration))
        }

    val noExplicitReturnTypeInApiModeWarning =
        diagnosticFixFactory(
            KtFirDiagnostic.NoExplicitReturnTypeInApiModeWarning::class,
            HLSpecifyExplicitTypeForCallableDeclarationIntention.applicator
        ) { diagnostic ->
            val callableDeclaration = diagnostic.psi as? KtCallableDeclaration ?: return@diagnosticFixFactory emptyList()
            listOf(callableDeclaration withInput getTypeInfo(callableDeclaration))
        }
}