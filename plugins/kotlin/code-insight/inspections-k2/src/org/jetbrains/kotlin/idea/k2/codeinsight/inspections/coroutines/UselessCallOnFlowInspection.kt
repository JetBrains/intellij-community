// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.coroutines

import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.collections.UselessCallOnCollectionInspection
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.coroutines.CoroutinesIds

internal class UselessCallOnFlowInspection : UselessCallOnCollectionInspection() {
    override val conversions: List<QualifiedFunctionCallConversion> =
        listOf(
            UselessFilterConversion(CoroutinesIds.Flows.filterNotNull),
            UselessFilterConversion(CoroutinesIds.Flows.filterIsInstance),

            UselessMapNotNullConversion(CoroutinesIds.Flows.mapNotNull, CoroutinesIds.Flows.map),
        )
}