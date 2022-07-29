// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.inspections.dfa

import com.intellij.codeInspection.dataFlow.value.RelationType
import org.jetbrains.kotlin.idea.codeInsight.hints.RangeKtExpressionType

fun RangeKtExpressionType.getRelationType() =
    when (this) {
        RangeKtExpressionType.rangeTo -> RelationType.GE to RelationType.LE
        RangeKtExpressionType.rangeUntil, RangeKtExpressionType.until -> RelationType.GE to RelationType.LT
        RangeKtExpressionType.downTo -> RelationType.LE to RelationType.GE
    }
