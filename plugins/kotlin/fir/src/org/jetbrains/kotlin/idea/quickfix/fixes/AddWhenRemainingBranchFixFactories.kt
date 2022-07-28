// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix.fixes

import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.analysis.api.symbols.KtClassKind
import org.jetbrains.kotlin.diagnostics.WhenMissingCase
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinApplicatorBasedQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.diagnosticFixFactory
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.AddRemainingWhenBranchesApplicator

object AddWhenRemainingBranchFixFactories {

    val noElseInWhen = diagnosticFixFactory(KtFirDiagnostic.NoElseInWhen::class) { diagnostic ->
        val whenExpression = diagnostic.psi
        val subjectExpression = whenExpression.subjectExpression ?: return@diagnosticFixFactory emptyList()

        buildList {
            val missingCases = diagnostic.missingWhenCases.takeIf {
                it.isNotEmpty() && it.singleOrNull() != WhenMissingCase.Unknown
            } ?: return@buildList

            add(
                KotlinApplicatorBasedQuickFix(
                    whenExpression,
                    AddRemainingWhenBranchesApplicator.Input(missingCases, null),
                    AddRemainingWhenBranchesApplicator.applicator
                )
            )
            val baseClassSymbol = subjectExpression.getKtType()?.expandedClassSymbol ?: return@buildList
            val enumToStarImport = baseClassSymbol.classIdIfNonLocal
            if (baseClassSymbol.classKind == KtClassKind.ENUM_CLASS && enumToStarImport != null) {
                add(
                    KotlinApplicatorBasedQuickFix(
                        whenExpression,
                        AddRemainingWhenBranchesApplicator.Input(missingCases, enumToStarImport),
                        AddRemainingWhenBranchesApplicator.applicatorUsingStarImport
                    )
                )
            }
        }
    }
}