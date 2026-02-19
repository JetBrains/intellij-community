// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

internal class SimplifiableCallInspection : AbstractSimplifiableCallInspection() {
    override val conversions: List<Conversion>
        get() = listOf(
            FlatMapToFlattenConversion(),
            FilterToFilterNotNullConversion(),
            FilterToFilterIsInstanceConversion(),
            MapNotNullToFilterIsInstanceConversion()
        )
}