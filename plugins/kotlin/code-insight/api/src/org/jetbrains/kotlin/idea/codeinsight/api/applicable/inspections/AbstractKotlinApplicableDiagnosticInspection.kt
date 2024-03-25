// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections

import org.jetbrains.kotlin.analysis.api.diagnostics.KtDiagnosticWithPsi
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.ApplicableRangesProvider
import org.jetbrains.kotlin.psi.KtElement
import kotlin.reflect.KClass

/**
 * [AbstractKotlinApplicableDiagnosticInspection] is a base interface for
 * [AbstractKotlinApplicableDiagnosticInspectionWithContext].
 *
 * TODO: Consider supporting multiple diagnostics.
 */
interface AbstractKotlinApplicableDiagnosticInspection<
    ELEMENT : KtElement,
    DIAGNOSTIC : KtDiagnosticWithPsi<ELEMENT>
        > : ApplicableRangesProvider<ELEMENT> {
    /**
     * The type of the [KtDiagnosticWithPsi] which should be filtered for.
     */
    fun getDiagnosticType(): KClass<DIAGNOSTIC>
}
