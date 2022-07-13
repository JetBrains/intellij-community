/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.quickfix.fixes

import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.diagnosticFixFactory
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.withInput
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.with
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.CallableReturnTypeUpdaterApplicator
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.CallableReturnTypeUpdaterApplicator.getTypeInfo
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtFunction

object SpecifyExplicitTypeFixFactories {
    private val applicator = CallableReturnTypeUpdaterApplicator.applicator.with {
        familyName(KotlinBundle.lazyMessage("specify.type.explicitly"))

        actionName { declaration, _ ->
            when (declaration) {
                is KtFunction -> KotlinBundle.message("specify.return.type.explicitly")
                else -> KotlinBundle.message("specify.type.explicitly")
            }
        }
    }

    val ambiguousAnonymousTypeInferred = diagnosticFixFactory(
        KtFirDiagnostic.AmbiguousAnonymousTypeInferred::class,
        applicator
    ) { diagnostic ->
        val declaration = diagnostic.psi as? KtCallableDeclaration ?: return@diagnosticFixFactory emptyList()
        listOf(declaration withInput getTypeInfo(declaration))
    }

    val noExplicitReturnTypeInApiMode =
        diagnosticFixFactory(
            KtFirDiagnostic.NoExplicitReturnTypeInApiMode::class,
            applicator
        ) { diagnostic ->
            val callableDeclaration = diagnostic.psi as? KtCallableDeclaration ?: return@diagnosticFixFactory emptyList()
            listOf(callableDeclaration withInput getTypeInfo(callableDeclaration))
        }

    val noExplicitReturnTypeInApiModeWarning =
        diagnosticFixFactory(
            KtFirDiagnostic.NoExplicitReturnTypeInApiModeWarning::class,
            applicator
        ) { diagnostic ->
            val callableDeclaration = diagnostic.psi as? KtCallableDeclaration ?: return@diagnosticFixFactory emptyList()
            listOf(callableDeclaration withInput getTypeInfo(callableDeclaration))
        }
}