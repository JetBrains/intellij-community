// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections

import org.jetbrains.kotlin.analysis.api.diagnostics.KaDiagnosticWithPsi
import org.jetbrains.kotlin.psi.KtElement

/**
 * A [KotlinApplicableInspectionBase.Simple] that applies to an element if it has a specific diagnostic [D].
 * Use this class when [D] is a `KaFirDiagnostic<Kt...>` and not a `KaFirDiagnostic<PsiElement>`.
 *
 * For `KaFirDiagnostic<PsiElement>`, use [KotlinPsiDiagnosticBasedInspectionBase].
 */
abstract class KotlinKtDiagnosticBasedInspectionBase<
        E : KtElement,
        D : KaDiagnosticWithPsi<E>,
        C : Any,
        > : KotlinPsiDiagnosticBasedInspectionBase<E, D, C>()