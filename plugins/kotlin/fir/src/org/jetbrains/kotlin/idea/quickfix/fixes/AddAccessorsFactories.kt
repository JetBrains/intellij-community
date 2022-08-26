// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix.fixes

import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicatorInput
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinApplicatorBasedQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.diagnosticFixFactories
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.AddAccessorApplicator
import org.jetbrains.kotlin.psi.KtProperty

object AddAccessorsFactories {
    val addAccessorsToUninitializedProperty =
        diagnosticFixFactories(
            KtFirDiagnostic.MustBeInitialized::class,
            KtFirDiagnostic.MustBeInitializedOrBeAbstract::class
        ) { diagnostic ->
            val property: KtProperty = diagnostic.psi
            val addGetter = property.getter == null
            val addSetter = property.isVar && property.setter == null
            if (!addGetter && !addSetter) return@diagnosticFixFactories emptyList()
            listOf(
                KotlinApplicatorBasedQuickFix(
                    property,
                    KotlinApplicatorInput.Empty,
                    AddAccessorApplicator.applicator(addGetter, addSetter),
                )
            )
        }
}