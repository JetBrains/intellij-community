// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.coroutines

import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.coroutines.CoroutinesIds
import org.jetbrains.kotlin.idea.k2.codeinsight.inspections.AbstractSimplifiableCallInspection

internal class SimplifiableFlowCallInspection : AbstractSimplifiableCallInspection() {
    override val conversions: List<Conversion>
        get() = listOf(
            FilterToFilterNotNullConversion(
                targetFqName = CoroutinesIds.Flows.filter.asSingleFqName(),
                replacementFqName = CoroutinesIds.Flows.filterNotNull.asSingleFqName(),
            ),
            FilterToFilterIsInstanceConversion(
                targetFqName = CoroutinesIds.Flows.filter.asSingleFqName(),
                replacementFqName = CoroutinesIds.Flows.filterIsInstance.asSingleFqName(),
            ),
        )
}